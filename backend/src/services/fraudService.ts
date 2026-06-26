import { db, collections } from '../config/firebase';
import { WatchEvent, FraudSignal } from '../models/schemas';

export class FraudService {
  /**
   * Evaluates a watch event for potential fraud signals.
   * Returns a risk score from 0 (Safe) to 100 (Extremely Suspicious).
   */
  static async evaluateWatchEvent(
    userId: string,
    watchEvent: Partial<WatchEvent>,
    fingerprint: { isEmulator: boolean; osVersion: string; hardware: string }
  ): Promise<{ riskScore: number; signals: Partial<FraudSignal>[] }> {
    const signals: Partial<FraudSignal>[] = [];
    let riskScore = 0;

    // 1. Check for Emulator Detection
    if (fingerprint.isEmulator) {
      signals.push({
        userId,
        signalType: 'emulator_fingerprint',
        severity: 'high',
        riskScoreImpact: 45,
        details: `Emulator fingerprint detected. OS: ${fingerprint.osVersion}, Hardware: ${fingerprint.hardware}`,
        isResolved: false
      });
      riskScore += 45;
    }

    // 2. Check for Abnormal Watch Speed
    // e.g. watched seconds exceeds elapsed wall time or is unrealistically high
    const watchedSeconds = watchEvent.watchedSeconds || 0;
    const startTimeMs = watchEvent.startTime?.toDate().getTime() || 0;
    const endTimeMs = watchEvent.endTime?.toDate().getTime() || Date.now();
    const elapsedSeconds = (endTimeMs - startTimeMs) / 1000;

    // If watch event records 60 seconds watched, but only 10 seconds elapsed
    if (watchedSeconds > elapsedSeconds * 1.5 && elapsedSeconds > 2) {
      signals.push({
        userId,
        watchEventId: watchEvent.id,
        signalType: 'abnormal_watch_speed',
        severity: 'high',
        riskScoreImpact: 40,
        details: `Abnormal watch speed: Client claimed ${watchedSeconds}s in ${elapsedSeconds.toFixed(1)}s elapsed time.`,
        isResolved: false
      });
      riskScore += 40;
    }

    // 3. Check for Duplicate Active Sessions
    const activeSessionsQuery = await collections.user_sessions
      .where('userId', '==', userId)
      .where('active', '==', true)
      .get();

    if (activeSessionsQuery.size > 2) {
      signals.push({
        userId,
        signalType: 'duplicate_session',
        severity: 'medium',
        riskScoreImpact: 20,
        details: `Duplicate active sessions: ${activeSessionsQuery.size} simultaneous sessions detected.`,
        isResolved: false
      });
      riskScore += 20;
    }

    // Record the fraud signals to Firestore if they exist
    if (signals.length > 0) {
      const batch = db.batch();
      for (const signal of signals) {
        const signalRef = collections.fraud_signals.doc();
        signal.id = signalRef.id;
        signal.timestamp = adminTimestamp();
        batch.set(signalRef, signal);
      }
      await batch.commit();
    }

    return {
      riskScore: Math.min(riskScore, 100),
      signals
    };
  }

  /**
   * Evaluate user's wallet withdraw risk before approval
   */
  static async evaluateWithdrawalRisk(userId: string, amount: number): Promise<number> {
    let riskScore = 0;

    // 1. Fetch unresolved high-severity fraud signals
    const fraudSignalsQuery = await collections.fraud_signals
      .where('userId', '==', userId)
      .where('isResolved', '==', false)
      .get();

    fraudSignalsQuery.forEach(doc => {
      const signal = doc.data() as FraudSignal;
      if (signal.severity === 'high') {
        riskScore += 30;
      } else if (signal.severity === 'medium') {
        riskScore += 15;
      }
    });

    // 2. Watch limit speed verification
    // Fetch total watched seconds today
    const startOfToday = new Date();
    startOfToday.setHours(0, 0, 0, 0);

    const watchHistoryQuery = await collections.watch_events
      .where('userId', '==', userId)
      .where('startTime', '>=', startOfToday)
      .get();

    let totalWatchedToday = 0;
    watchHistoryQuery.forEach(doc => {
      const event = doc.data() as WatchEvent;
      totalWatchedToday += event.watchedSeconds || 0;
    });

    // If watched seconds today is more than 16 hours (57600 seconds) - highly bot-like
    if (totalWatchedToday > 57600) {
      riskScore += 35;
    }

    return Math.min(riskScore, 100);
  }
}

function adminTimestamp() {
  return FirebaseFirestore.Timestamp.now();
}
