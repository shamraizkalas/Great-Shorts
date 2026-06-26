import { db, collections } from '../config/firebase';
import { RewardRule, RewardLedger, WalletAccount, WalletTransaction, Referral } from '../models/schemas';

export class RewardService {
  /**
   * Retrieves an active reward rule from the database or returns a hardcoded safe fallback.
   */
  static async getRuleValue(ruleId: string, fallbackValue: number): Promise<number> {
    try {
      const doc = await collections.reward_rules.doc(ruleId).get();
      if (doc.exists) {
        const rule = doc.data() as RewardRule;
        if (rule.isActive) {
          return rule.value;
        }
      }
    } catch (error) {
      console.error(`Error loading reward rule ${ruleId}:`, error);
    }
    return fallbackValue;
  }

  /**
   * Post reward earning to ledger and update wallet balance atomically
   */
  static async creditEarning(
    userId: string,
    amount: number,
    type: RewardLedger['type'],
    referenceId: string,
    description: string
  ): Promise<void> {
    if (amount <= 0) return;

    await db.runTransaction(async (transaction) => {
      // 1. Create a ledger entry reference
      const ledgerRef = collections.reward_ledger.doc();
      const ledgerEntry: RewardLedger = {
        id: ledgerRef.id,
        userId,
        amount,
        type,
        referenceId,
        timestamp: FirebaseFirestore.Timestamp.now(),
        description
      };

      // 2. Fetch or create user's wallet
      const walletRef = collections.wallet_accounts.doc(userId);
      const walletSnap = await transaction.get(walletRef);

      let wallet: WalletAccount;
      if (!walletSnap.exists) {
        wallet = {
          userId,
          availableBalance: amount,
          pendingBalance: 0,
          lifetimeEarned: amount,
          totalWithdrawn: 0,
          updatedAt: FirebaseFirestore.Timestamp.now()
        };
      } else {
        const currentWallet = walletSnap.data() as WalletAccount;
        wallet = {
          ...currentWallet,
          availableBalance: currentWallet.availableBalance + amount,
          lifetimeEarned: currentWallet.lifetimeEarned + amount,
          updatedAt: FirebaseFirestore.Timestamp.now()
        };
      }

      // 3. Create wallet transaction log
      const txRef = collections.wallet_transactions.doc();
      const txEntry: WalletTransaction = {
        id: txRef.id,
        userId,
        amount,
        type: 'credit_earning',
        status: 'completed',
        referenceId: ledgerRef.id,
        timestamp: FirebaseFirestore.Timestamp.now(),
        description
      };

      // 4. Commit everything atomically
      transaction.set(ledgerRef, ledgerEntry);
      transaction.set(walletRef, wallet);
      transaction.set(txRef, txEntry);
    });

    // 5. Send in-app notification
    try {
      const notificationRef = collections.notifications.doc();
      await notificationRef.set({
        id: notificationRef.id,
        userId,
        title: 'Rewards Credited! 🎉',
        message: `${description}: PKR ${amount.toFixed(2)} added to your wallet.`,
        type: 'reward_credited',
        isRead: false,
        timestamp: FirebaseFirestore.Timestamp.now()
      });
    } catch (e) {
      console.error('Failed to write reward notification:', e);
    }
  }

  /**
   * Process referral trigger when user does their first valid watch event
   */
  static async checkAndProcessReferralMilestone(userId: string): Promise<void> {
    // 1. Query if there is a referral entry for this user where state is 'joined'
    const referralQuery = await collections.referrals
      .where('referredUserId', '==', userId)
      .where('status', '==', 'joined')
      .get();

    if (referralQuery.empty) return;

    const referralDoc = referralQuery.docs[0];
    const referral = referralDoc.data() as Referral;

    // 2. Reward values from admin controls or standard fallback
    const referrerReward = await this.getRuleValue('referral_referrer_reward', 50); // 50 PKR
    const refereeReward = await this.getRuleValue('referral_referee_reward', 20); // 20 PKR

    // 3. Update referral status to 'qualified'
    await referralDoc.ref.update({
      status: 'qualified',
      qualifiedTimestamp: FirebaseFirestore.Timestamp.now(),
      bonusAmount: referrerReward
    });

    // 4. Credit referrer and referee
    await this.creditEarning(
      referral.referrerUserId,
      referrerReward,
      'referral_bonus',
      referralDoc.id,
      `Referral bonus for inviting user (UID: ${userId.slice(0, 6)})`
    );

    await this.creditEarning(
      userId,
      refereeReward,
      'referral_bonus',
      referralDoc.id,
      `Signup referral bonus via invitation`
    );
  }
}
