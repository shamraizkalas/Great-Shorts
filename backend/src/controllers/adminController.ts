import { Response } from 'express';
import { AuthenticatedRequest } from '../middleware/auth';
import { collections, db, auth as adminAuth } from '../config/firebase';
import { Withdrawal, WalletAccount, AuditLog, Video, RewardRule, User } from '../models/schemas';

export class AdminController {
  /**
   * Helper to write Immutable Audit Logs
   */
  static async logAdminAction(
    adminId: string,
    action: AuditLog['action'],
    targetId: string,
    details: string
  ) {
    try {
      const ref = collections.audit_logs.doc();
      const auditLog: AuditLog = {
        id: ref.id,
        adminUserId: adminId,
        action,
        targetId,
        details,
        timestamp: FirebaseFirestore.Timestamp.now()
      };
      await ref.set(auditLog);
    } catch (e) {
      console.error('Failed to write audit log:', e);
    }
  }

  static async addVideo(req: AuthenticatedRequest, res: Response) {
    const adminId = req.user!.uid;
    const { title, description, videoUrl, thumbnailUrl, durationSeconds, categoryId, isPremium } = req.body;

    try {
      const ref = collections.videos.doc();
      const video: Video = {
        id: ref.id,
        title,
        description,
        videoUrl,
        thumbnailUrl,
        durationSeconds,
        categoryId,
        isPremium: isPremium || false,
        isSoftDeleted: false,
        createdAt: FirebaseFirestore.Timestamp.now(),
        viewsCount: 0,
        likesCount: 0
      };

      await ref.set(video);
      await AdminController.logAdminAction(adminId, 'add_video', ref.id, `Added video: ${title}`);

      return res.status(201).json({ success: true, data: video });
    } catch (e: any) {
      return res.status(500).json({ success: false, error: { message: e.message } });
    }
  }

  static async updateVideo(req: AuthenticatedRequest, res: Response) {
    const adminId = req.user!.uid;
    const { id } = req.params;

    try {
      await collections.videos.doc(id).update({
        ...req.body,
        updatedAt: FirebaseFirestore.Timestamp.now()
      });
      await AdminController.logAdminAction(adminId, 'add_video', id, `Updated video parameters`);
      return res.status(200).json({ success: true, message: 'Video parameters updated' });
    } catch (e: any) {
      return res.status(500).json({ success: false, error: { message: e.message } });
    }
  }

  static async updateRewardRule(req: AuthenticatedRequest, res: Response) {
    const adminId = req.user!.uid;
    const { id } = req.params; // e.g. min_withdrawal_pkr
    const { value, isActive } = req.body;

    try {
      const ref = collections.reward_rules.doc(id);
      const ruleUpdate: Partial<RewardRule> = {
        value,
        isActive,
        updatedAt: FirebaseFirestore.Timestamp.now(),
        updatedBy: adminId
      };

      await ref.update(ruleUpdate);
      await AdminController.logAdminAction(adminId, 'update_reward_rules', id, `Updated reward rule value to ${value}`);

      return res.status(200).json({ success: true, message: 'Reward rule updated' });
    } catch (e: any) {
      return res.status(500).json({ success: false, error: { message: e.message } });
    }
  }

  static async getWithdrawalRequests(req: AuthenticatedRequest, res: Response) {
    try {
      const snapshot = await collections.withdrawals
        .orderBy('createdAt', 'desc')
        .get();

      const requests: any[] = [];
      snapshot.forEach(doc => {
        requests.push({ id: doc.id, ...doc.data() });
      });

      return res.status(200).json({ success: true, data: requests });
    } catch (e: any) {
      return res.status(500).json({ success: false, error: { message: e.message } });
    }
  }

  static async approveWithdrawal(req: AuthenticatedRequest, res: Response) {
    const adminId = req.user!.uid;
    const { id } = req.params;

    try {
      const withdrawalRef = collections.withdrawals.doc(id);
      const withdrawalSnap = await withdrawalRef.get();
      if (!withdrawalSnap.exists) {
        return res.status(404).json({ success: false, error: { message: 'Withdrawal not found' } });
      }

      const w = withdrawalSnap.data() as Withdrawal;
      if (w.status !== 'pending') {
        return res.status(400).json({ success: false, error: { message: 'Withdrawal is already processed' } });
      }

      // Execute atomic transaction to clear pending hold and move balance out permanently
      const walletRef = collections.wallet_accounts.doc(w.userId);

      await db.runTransaction(async (transaction) => {
        const walletSnap = await transaction.get(walletRef);
        const wallet = walletSnap.data() as WalletAccount;

        const updatedWallet: WalletAccount = {
          ...wallet,
          pendingBalance: wallet.pendingBalance - w.amount,
          totalWithdrawn: wallet.totalWithdrawn + w.amount,
          updatedAt: FirebaseFirestore.Timestamp.now()
        };

        const txRef = collections.wallet_transactions.doc();
        const txLog = {
          id: txRef.id,
          userId: w.userId,
          amount: -w.amount,
          type: 'debit_withdrawal',
          status: 'completed',
          referenceId: id,
          timestamp: FirebaseFirestore.Timestamp.now(),
          description: `Withdrawal of PKR ${w.amount.toFixed(2)} approved and dispatched.`
        };

        transaction.update(withdrawalRef, {
          status: 'approved',
          processedAt: FirebaseFirestore.Timestamp.now(),
          processedBy: adminId
        });
        transaction.set(walletRef, updatedWallet);
        transaction.set(txRef, txLog);
      });

      await AdminController.logAdminAction(adminId, 'approve_withdrawal', id, `Approved withdrawal request of PKR ${w.amount}`);

      // Dispatch Notification
      try {
        const notifRef = collections.notifications.doc();
        await notifRef.set({
          id: notifRef.id,
          userId: w.userId,
          title: 'Withdrawal Approved! 💸',
          message: `Your withdrawal of PKR ${w.amount.toFixed(2)} was successfully processed and dispatched. Check account.`,
          type: 'withdrawal_status',
          isRead: false,
          timestamp: FirebaseFirestore.Timestamp.now()
        });
      } catch (e) {
        console.error(e);
      }

      return res.status(200).json({ success: true, message: 'Withdrawal approved successfully' });
    } catch (e: any) {
      return res.status(500).json({ success: false, error: { message: e.message } });
    }
  }

  static async rejectWithdrawal(req: AuthenticatedRequest, res: Response) {
    const adminId = req.user!.uid;
    const { id } = req.params;
    const { reason } = req.body;

    try {
      const withdrawalRef = collections.withdrawals.doc(id);
      const withdrawalSnap = await withdrawalRef.get();
      if (!withdrawalSnap.exists) {
        return res.status(404).json({ success: false, error: { message: 'Withdrawal not found' } });
      }

      const w = withdrawalSnap.data() as Withdrawal;
      if (w.status !== 'pending') {
        return res.status(400).json({ success: false, error: { message: 'Withdrawal is already processed' } });
      }

      const walletRef = collections.wallet_accounts.doc(w.userId);

      await db.runTransaction(async (transaction) => {
        const walletSnap = await transaction.get(walletRef);
        const wallet = walletSnap.data() as WalletAccount;

        // Revert pending amount back to available balance
        const updatedWallet: WalletAccount = {
          ...wallet,
          pendingBalance: wallet.pendingBalance - w.amount,
          availableBalance: wallet.availableBalance + w.amount,
          updatedAt: FirebaseFirestore.Timestamp.now()
        };

        const txRef = collections.wallet_transactions.doc();
        const txLog = {
          id: txRef.id,
          userId: w.userId,
          amount: w.amount,
          type: 'adjustment_hold',
          status: 'reversed',
          referenceId: id,
          timestamp: FirebaseFirestore.Timestamp.now(),
          description: `Withdrawal request rejected. Funds returned to balance. Reason: ${reason || 'None provided'}`
        };

        transaction.update(withdrawalRef, {
          status: 'rejected',
          adminNotes: reason || 'None provided',
          processedAt: FirebaseFirestore.Timestamp.now(),
          processedBy: adminId
        });
        transaction.set(walletRef, updatedWallet);
        transaction.set(txRef, txLog);
      });

      await AdminController.logAdminAction(adminId, 'reject_withdrawal', id, `Rejected withdrawal request of PKR ${w.amount}. Reason: ${reason}`);

      // Dispatch Notification
      try {
        const notifRef = collections.notifications.doc();
        await notifRef.set({
          id: notifRef.id,
          userId: w.userId,
          title: 'Withdrawal Rejected ❌',
          message: `Your withdrawal of PKR ${w.amount.toFixed(2)} was rejected. Reason: ${reason || 'Failed security check'}.`,
          type: 'withdrawal_status',
          isRead: false,
          timestamp: FirebaseFirestore.Timestamp.now()
        });
      } catch (e) {
        console.error(e);
      }

      return res.status(200).json({ success: true, message: 'Withdrawal rejected. Balance refunded.' });
    } catch (e: any) {
      return res.status(500).json({ success: false, error: { message: e.message } });
    }
  }

  static async blockUser(req: AuthenticatedRequest, res: Response) {
    const adminId = req.user!.uid;
    const { id } = req.params; // target userId

    try {
      await collections.users.doc(id).update({
        isBlocked: true,
        status: 'blocked',
        updatedAt: FirebaseFirestore.Timestamp.now()
      });
      
      // Revoke any active sessions or force token refresh via claims
      await adminAuth.setCustomUserClaims(id, { role: 'user', blocked: true });
      await AdminController.logAdminAction(adminId, 'block_user', id, 'Blocked user account and set blocked custom claim');

      return res.status(200).json({ success: true, message: 'User blocked' });
    } catch (e: any) {
      return res.status(500).json({ success: false, error: { message: e.message } });
    }
  }

  static async unblockUser(req: AuthenticatedRequest, res: Response) {
    const adminId = req.user!.uid;
    const { id } = req.params;

    try {
      await collections.users.doc(id).update({
        isBlocked: false,
        status: 'active',
        updatedAt: FirebaseFirestore.Timestamp.now()
      });
      await adminAuth.setCustomUserClaims(id, { role: 'user' });
      await AdminController.logAdminAction(adminId, 'unblock_user', id, 'Unblocked user account and cleared blocked custom claim');

      return res.status(200).json({ success: true, message: 'User unblocked' });
    } catch (e: any) {
      return res.status(500).json({ success: false, error: { message: e.message } });
    }
  }

  static async createAnnouncement(req: AuthenticatedRequest, res: Response) {
    const { titleEnglish, titleUrdu, contentEnglish, contentUrdu } = req.body;

    try {
      const ref = collections.announcements.doc();
      const announcement = {
        id: ref.id,
        titleEnglish,
        titleUrdu,
        contentEnglish,
        contentUrdu,
        createdAt: FirebaseFirestore.Timestamp.now(),
        active: true
      };

      await ref.set(announcement);

      // Distribute notification to all active users would typically happen as a background worker.
      // Here, we save the announcement for list-fetching.
      return res.status(201).json({ success: true, data: announcement });
    } catch (e: any) {
      return res.status(500).json({ success: false, error: { message: e.message } });
    }
  }

  static async getFraudSignals(req: AuthenticatedRequest, res: Response) {
    try {
      const snapshot = await collections.fraud_signals
        .orderBy('timestamp', 'desc')
        .limit(100)
        .get();

      const list: any[] = [];
      snapshot.forEach(doc => {
        list.push({ id: doc.id, ...doc.data() });
      });

      return res.status(200).json({ success: true, data: list });
    } catch (e: any) {
      return res.status(500).json({ success: false, error: { message: e.message } });
    }
  }

  static async getAuditLogs(req: AuthenticatedRequest, res: Response) {
    try {
      const snapshot = await collections.audit_logs
        .orderBy('timestamp', 'desc')
        .limit(100)
        .get();

      const list: any[] = [];
      snapshot.forEach(doc => {
        list.push({ id: doc.id, ...doc.data() });
      });

      return res.status(200).json({ success: true, data: list });
    } catch (e: any) {
      return res.status(500).json({ success: false, error: { message: e.message } });
    }
  }
}
