import { Response } from 'express';
import { AuthenticatedRequest } from '../middleware/auth';
import { collections } from '../config/firebase';
import { WalletAccount } from '../models/schemas';

export class WalletController {
  static async getWallet(req: AuthenticatedRequest, res: Response) {
    const uid = req.user?.uid;
    if (!uid) return res.status(401).json({ success: false, error: { message: 'Unauthorized' } });

    try {
      const walletDoc = await collections.wallet_accounts.doc(uid).get();
      
      let wallet: WalletAccount;
      if (!walletDoc.exists) {
        wallet = {
          userId: uid,
          availableBalance: 0,
          pendingBalance: 0,
          lifetimeEarned: 0,
          totalWithdrawn: 0,
          updatedAt: FirebaseFirestore.Timestamp.now()
        };
        await collections.wallet_accounts.doc(uid).set(wallet);
      } else {
        wallet = walletDoc.data() as WalletAccount;
      }

      return res.status(200).json({
        success: true,
        data: wallet
      });
    } catch (e: any) {
      return res.status(500).json({ success: false, error: { message: e.message } });
    }
  }

  static async getTransactions(req: AuthenticatedRequest, res: Response) {
    const uid = req.user?.uid;
    if (!uid) return res.status(401).json({ success: false, error: { message: 'Unauthorized' } });

    try {
      const snapshot = await collections.wallet_transactions
        .where('userId', '==', uid)
        .orderBy('timestamp', 'desc')
        .limit(50)
        .get();

      const transactions: any[] = [];
      snapshot.forEach(doc => {
        transactions.push({ id: doc.id, ...doc.data() });
      });

      return res.status(200).json({
        success: true,
        data: transactions
      });
    } catch (e: any) {
      return res.status(500).json({ success: false, error: { message: e.message } });
    }
  }
}
