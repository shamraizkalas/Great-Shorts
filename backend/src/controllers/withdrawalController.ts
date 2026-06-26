import { Response } from 'express';
import { AuthenticatedRequest } from '../middleware/auth';
import { collections, db } from '../config/firebase';
import { WalletAccount, Withdrawal, WalletTransaction } from '../models/schemas';
import { FraudService } from '../services/fraudService';
import { RewardService } from '../services/rewardService';

export class WithdrawalController {
  /**
   * Request payout/withdrawal. Check available balance and evaluate fraud score before logging.
   */
  static async requestWithdrawal(req: AuthenticatedRequest, res: Response) {
    const uid = req.user?.uid;
    const { amount, method, accountNumber, accountTitle, bankName } = req.body;
    if (!uid) return res.status(401).json({ success: false, error: { message: 'Unauthorized' } });

    try {
      // 1. Fetch user's wallet and verify balance
      const walletRef = collections.wallet_accounts.doc(uid);
      const walletSnap = await walletRef.get();
      if (!walletSnap.exists) {
        return res.status(400).json({
          success: false,
          error: { message: 'No wallet registered. Play videos to earn first!', code: 'NO_WALLET' }
        });
      }

      const wallet = walletSnap.data() as WalletAccount;
      if (wallet.availableBalance < amount) {
        return res.status(400).json({
          success: false,
          error: { message: `Insufficient funds. Your available balance is PKR ${wallet.availableBalance.toFixed(2)}.`, code: 'INSUFFICIENT_FUNDS' }
        });
      }

      // 2. Minimum withdrawal threshold (e.g. 100 PKR)
      const minThreshold = await RewardService.getRuleValue('min_withdrawal_pkr', 100);
      if (amount < minThreshold) {
        return res.status(400).json({
          success: false,
          error: { message: `Minimum withdrawal amount is PKR ${minThreshold}.`, code: 'BELOW_MIN_THRESHOLD' }
        });
      }

      // 3. Evaluate fraud signals and generate withdrawal risk score
      const riskScore = await FraudService.evaluateWithdrawalRisk(uid, amount);

      // 4. Create withdrawal request and deduct wallet balance inside an atomic transaction
      const withdrawalRef = collections.withdrawals.doc();
      const withdrawal: Withdrawal = {
        id: withdrawalRef.id,
        userId: uid,
        amount,
        method,
        accountDetails: {
          accountNumber,
          accountTitle,
          bankName
        },
        status: 'pending',
        riskScore,
        createdAt: FirebaseFirestore.Timestamp.now()
      };

      await db.runTransaction(async (transaction) => {
        // Fetch wallet again to ensure thread-safety
        const freshWalletSnap = await transaction.get(walletRef);
        const freshWallet = freshWalletSnap.data() as WalletAccount;

        if (freshWallet.availableBalance < amount) {
          throw new Error('Insufficient funds checked inside transaction');
        }

        // Deduct from available balance, add to pending
        const updatedWallet: WalletAccount = {
          ...freshWallet,
          availableBalance: freshWallet.availableBalance - amount,
          pendingBalance: freshWallet.pendingBalance + amount,
          updatedAt: FirebaseFirestore.Timestamp.now()
        };

        // Create transaction history log
        const txRef = collections.wallet_transactions.doc();
        const tx: WalletTransaction = {
          id: txRef.id,
          userId: uid,
          amount: -amount,
          type: 'debit_withdrawal',
          status: 'pending',
          referenceId: withdrawalRef.id,
          timestamp: FirebaseFirestore.Timestamp.now(),
          description: `Withdrawal request submitted via ${method.toUpperCase()}`
        };

        transaction.set(withdrawalRef, withdrawal);
        transaction.set(walletRef, updatedWallet);
        transaction.set(txRef, tx);
      });

      // 5. Dispatch Notification
      try {
        const notifRef = collections.notifications.doc();
        await notifRef.set({
          id: notifRef.id,
          userId: uid,
          title: 'Withdrawal Submitted ⏳',
          message: `Your request of PKR ${amount.toFixed(2)} is pending approval. Risk score checked.`,
          type: 'withdrawal_status',
          isRead: false,
          timestamp: FirebaseFirestore.Timestamp.now()
        });
      } catch (e) {
        console.error('Notification log failure:', e);
      }

      return res.status(201).json({
        success: true,
        data: withdrawal
      });
    } catch (e: any) {
      return res.status(500).json({ success: false, error: { message: e.message } });
    }
  }

  static async getWithdrawalHistory(req: AuthenticatedRequest, res: Response) {
    const uid = req.user?.uid;
    if (!uid) return res.status(401).json({ success: false, error: { message: 'Unauthorized' } });

    try {
      const snapshot = await collections.withdrawals
        .where('userId', '==', uid)
        .orderBy('createdAt', 'desc')
        .get();

      const history: any[] = [];
      snapshot.forEach(doc => {
        history.push({ id: doc.id, ...doc.data() });
      });

      return res.status(200).json({
        success: true,
        data: history
      });
    } catch (e: any) {
      return res.status(500).json({ success: false, error: { message: e.message } });
    }
  }
}
