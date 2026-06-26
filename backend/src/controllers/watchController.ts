import { Response } from 'express';
import { AuthenticatedRequest } from '../middleware/auth';
import { collections, db } from '../config/firebase';
import { WatchEvent, Streak, UserSession } from '../models/schemas';
import { FraudService } from '../services/fraudService';
import { RewardService } from '../services/rewardService';

export class WatchController {
  /**
   * Post watch/start - Records beginning of a viewing session to prevent retroactive claims
   */
  static async startWatchSession(req: AuthenticatedRequest, res: Response) {
    const uid = req.user?.uid;
    const { videoId, sessionId, deviceId } = req.body;
    if (!uid) return res.status(401).json({ success: false, error: { message: 'Unauthorized' } });

    try {
      // Create session document
      const sessionRef = collections.user_sessions.doc(sessionId);
      const sessionEntry: UserSession = {
        id: sessionId,
        userId: uid,
        deviceId,
        ipAddress: req.ip || 'Unknown',
        userAgent: req.headers['user-agent'] || 'Unknown',
        active: true,
        createdAt: FirebaseFirestore.Timestamp.now(),
        lastActiveAt: FirebaseFirestore.Timestamp.now()
      };
      await sessionRef.set(sessionEntry);

      // Create initial watch event
      const watchEventRef = collections.watch_events.doc();
      const watchEvent: WatchEvent = {
        id: watchEventRef.id,
        userId: uid,
        videoId,
        sessionId,
        startTime: FirebaseFirestore.Timestamp.now(),
        endTime: FirebaseFirestore.Timestamp.now(), // default placeholder
        watchedSeconds: 0,
        completionPercentage: 0,
        deviceId,
        ipAddress: req.ip || 'Unknown',
        userAgent: req.headers['user-agent'] || 'Unknown',
        rewardStatus: 'pending',
        rewardAmount: 0
      };
      await watchEventRef.set(watchEvent);

      return res.status(201).json({
        success: true,
        data: { watchEventId: watchEventRef.id, session: sessionEntry }
      });
    } catch (e: any) {
      return res.status(500).json({ success: false, error: { message: e.message } });
    }
  }

  /**
   * Post watch/finish - Submits watch time, executes fraud engine, credits rewards safely.
   * NEVER trust client-side claims blindly.
   */
  static async finishWatchSession(req: AuthenticatedRequest, res: Response) {
    const uid = req.user?.uid;
    const { videoId, sessionId, watchedSeconds, completionPercentage, deviceId, fingerprint } = req.body;
    if (!uid) return res.status(401).json({ success: false, error: { message: 'Unauthorized' } });

    try {
      // 1. Fetch the corresponding user session
      const sessionRef = collections.user_sessions.doc(sessionId);
      const sessionSnap = await sessionRef.get();
      if (!sessionSnap.exists) {
        return res.status(400).json({
          success: false,
          error: { message: 'Invalid session context.', code: 'INVALID_SESSION' }
        });
      }

      // Close session
      await sessionRef.update({
        active: false,
        lastActiveAt: FirebaseFirestore.Timestamp.now()
      });

      // 2. Fetch the corresponding watch event
      const watchQuery = await collections.watch_events
        .where('userId', '==', uid)
        .where('sessionId', '==', sessionId)
        .where('videoId', '==', videoId)
        .limit(1)
        .get();

      if (watchQuery.empty) {
        return res.status(400).json({
          success: false,
          error: { message: 'No matching watch trace found.', code: 'NO_WATCH_TRACE' }
        });
      }

      const watchDoc = watchQuery.docs[0];
      const watchData = watchDoc.data() as WatchEvent;

      if (watchData.rewardStatus !== 'pending') {
        return res.status(400).json({
          success: false,
          error: { message: 'This watch event has already been processed.', code: 'ALREADY_PROCESSED' }
        });
      }

      const finishedEvent: Partial<WatchEvent> = {
        id: watchDoc.id,
        endTime: FirebaseFirestore.Timestamp.now(),
        watchedSeconds,
        completionPercentage,
        rewardStatus: 'pending'
      };

      // 3. SECURE VERIFICATION: Evaluate risk with FraudService
      const { riskScore, signals } = await FraudService.evaluateWatchEvent(uid, finishedEvent, fingerprint);

      // If risk score is too high (e.g. >= 60), we hold the reward for manual admin review
      if (riskScore >= 60) {
        await watchDoc.ref.update({
          ...finishedEvent,
          rewardStatus: 'failed_fraud_held',
          rewardAmount: 0
        });

        return res.status(200).json({
          success: true,
          data: {
            status: 'held_for_review',
            riskScore,
            message: 'Earning transaction flagged and held for security review.'
          }
        });
      }

      // 4. Calculate watch reward amount
      // Rule: Watch bonus per second (e.g., 0.05 PKR per second -> 3 PKR a minute)
      const multiplier = await RewardService.getRuleValue('watch_pkr_per_sec', 0.05);
      const rewardAmount = watchedSeconds * multiplier;

      // Update watch event to verified and credited
      await watchDoc.ref.update({
        ...finishedEvent,
        rewardStatus: 'verified_credited',
        rewardAmount
      });

      // 5. Credit user's wallet dynamically
      await RewardService.creditEarning(
        uid,
        rewardAmount,
        'watch_bonus',
        watchDoc.id,
        `Watch earnings for video ID: ${videoId.slice(0, 6)}`
      );

      // 6. Check if user is referred and has now completed their first qualifying watch event
      await RewardService.checkAndProcessReferralMilestone(uid);

      return res.status(200).json({
        success: true,
        data: {
          status: 'credited',
          riskScore,
          amountCredited: rewardAmount
        }
      });
    } catch (e: any) {
      return res.status(500).json({ success: false, error: { message: e.message } });
    }
  }

  /**
   * Endpoint to claim ad bonuses or daily check-ins directly from admin rules
   */
  static async claimBonus(req: AuthenticatedRequest, res: Response) {
    const uid = req.user?.uid;
    const { claimType } = req.body; // e.g. daily_check_in
    if (!uid) return res.status(401).json({ success: false, error: { message: 'Unauthorized' } });

    try {
      if (claimType === 'daily_check_in') {
        const bonusAmount = await RewardService.getRuleValue('daily_check_in_bonus', 10); // 10 PKR

        // Check if already checked in today
        const startOfToday = new Date();
        startOfToday.setHours(0, 0, 0, 0);

        const checkedInSnap = await collections.reward_ledger
          .where('userId', '==', uid)
          .where('type', '==', 'daily_check_in')
          .where('timestamp', '>=', startOfToday)
          .get();

        if (!checkedInSnap.empty) {
          return res.status(400).json({
            success: false,
            error: { message: 'You have already claimed your daily check-in reward today.', code: 'ALREADY_CLAIMED' }
          });
        }

        const claimRef = collections.reward_ledger.doc();
        await RewardService.creditEarning(
          uid,
          bonusAmount,
          'daily_check_in',
          claimRef.id,
          'Daily check-in reward'
        );

        return res.status(200).json({
          success: true,
          data: { bonusAmount }
        });
      }

      return res.status(400).json({ success: false, error: { message: 'Invalid claim type' } });
    } catch (e: any) {
      return res.status(500).json({ success: false, error: { message: e.message } });
    }
  }

  /**
   * Handles daily check-ins and tracks user streaks
   */
  static async dailyStreakCheckIn(req: AuthenticatedRequest, res: Response) {
    const uid = req.user?.uid;
    if (!uid) return res.status(401).json({ success: false, error: { message: 'Unauthorized' } });

    try {
      const streakRef = collections.streaks.doc(uid);
      const streakSnap = await streakRef.get();

      const todayStr = new Date().toISOString().slice(0, 10); // YYYY-MM-DD
      const yesterday = new Date();
      yesterday.setDate(yesterday.getDate() - 1);
      const yesterdayStr = yesterday.toISOString().slice(0, 10);

      let currentStreak = 1;
      let history: string[] = [todayStr];

      if (streakSnap.exists) {
        const streakData = streakSnap.data() as Streak;
        
        if (streakData.streakHistory.includes(todayStr)) {
          return res.status(400).json({
            success: false,
            error: { message: 'Already checked in today!', code: 'ALREADY_CHECKED_IN' }
          });
        }

        if (streakData.streakHistory.includes(yesterdayStr)) {
          // Streak continuous
          currentStreak = streakData.currentStreakDays + 1;
        } else {
          // Streak broken
          currentStreak = 1;
        }

        history = [...streakData.streakHistory, todayStr];
      }

      const updatedStreak: Streak = {
        userId: uid,
        currentStreakDays: currentStreak,
        lastCheckInAt: FirebaseFirestore.Timestamp.now(),
        streakHistory: history
      };

      await streakRef.set(updatedStreak);

      // Trigger streak bonus on milestones: e.g. every 7 days -> bonus 50 PKR
      let bonusAmount = 5; // standard check-in streak bonus
      if (currentStreak % 7 === 0) {
        bonusAmount = await RewardService.getRuleValue('streak_7day_bonus', 50);
      }

      await RewardService.creditEarning(
        uid,
        bonusAmount,
        'streak_bonus',
        `streak_${currentStreak}_${todayStr}`,
        `${currentStreak}-Day Watch Streak reward!`
      );

      return res.status(200).json({
        success: true,
        data: {
          currentStreakDays: currentStreak,
          rewardCredited: bonusAmount
        }
      });
    } catch (e: any) {
      return res.status(500).json({ success: false, error: { message: e.message } });
    }
  }
}
