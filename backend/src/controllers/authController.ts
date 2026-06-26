import { Response } from 'express';
import { AuthenticatedRequest } from '../middleware/auth';
import { collections, auth as adminAuth } from '../config/firebase';
import { User, Referral } from '../models/schemas';

export class AuthController {
  /**
   * Register a new user in Firestore and configure custom claims
   */
  static async register(req: AuthenticatedRequest, res: Response) {
    const { email, phone, displayName, referralCode } = req.body;
    
    // We assume the user has authenticated in Firebase Auth on the client
    // and passed their details. Here, we register their metadata in Firestore.
    try {
      const uid = req.user?.uid;
      if (!uid) {
        return res.status(400).json({
          success: false,
          error: { message: 'Authentication identity missing from context.', code: 'ID_MISSING' }
        });
      }

      // Check if user document already exists
      const userDoc = await collections.users.doc(uid).get();
      if (userDoc.exists) {
        return res.status(409).json({
          success: false,
          error: { message: 'User profile already registered.', code: 'USER_EXISTS' }
        });
      }

      // Generate a unique 8-character referral code for this new user
      const selfReferralCode = Math.random().toString(36).substring(2, 10).toUpperCase();

      // Setup clean User object
      const newUser: User = {
        id: uid,
        email: email || req.user?.email || null,
        phone: phone || req.user?.phone || null,
        displayName: displayName || 'GreatShort Viewer',
        photoURL: null,
        role: 'user',
        createdAt: FirebaseFirestore.Timestamp.now(),
        updatedAt: FirebaseFirestore.Timestamp.now(),
        isBlocked: false,
        status: 'active',
        referralCode: selfReferralCode,
        accountLevel: 1
      };

      // Handle referral code signup if present
      if (referralCode) {
        const referrerQuery = await collections.users
          .where('referralCode', '==', referralCode.toUpperCase())
          .get();
        
        if (!referrerQuery.empty) {
          const referrerDoc = referrerQuery.docs[0];
          newUser.referredBy = referralCode.toUpperCase();

          // Create a referral tree node
          const referralRef = collections.referrals.doc();
          const referralEntry: Referral = {
            id: referralRef.id,
            referrerUserId: referrerDoc.id,
            referredUserId: uid,
            referralCodeUsed: referralCode.toUpperCase(),
            status: 'joined',
            bonusAmount: 0,
            timestamp: FirebaseFirestore.Timestamp.now()
          };
          await referralRef.set(referralEntry);
        }
      }

      await collections.users.doc(uid).set(newUser);

      // Set custom user claim "role" to "user"
      await adminAuth.setCustomUserClaims(uid, { role: 'user' });

      return res.status(201).json({
        success: true,
        data: newUser
      });
    } catch (error: any) {
      return res.status(500).json({
        success: false,
        error: { message: error.message || 'Registration failed.', code: 'REGISTRATION_ERROR' }
      });
    }
  }

  /**
   * Verify token details on login and set claims if needed
   */
  static async login(req: AuthenticatedRequest, res: Response) {
    // Standard validation; since we use Firebase Auth idTokens,
    // actual auth verification is done by middleware.
    // This route serves to verify existing custom claims or establish session logs.
    const uid = req.user?.uid;
    if (!uid) {
      return res.status(401).json({
        success: false,
        error: { message: 'Unauthorized.', code: 'UNAUTHORIZED' }
      });
    }

    try {
      const userSnap = await collections.users.doc(uid).get();
      if (!userSnap.exists) {
        return res.status(404).json({
          success: false,
          error: { message: 'User profile not found in database.', code: 'USER_NOT_FOUND' }
        });
      }

      const user = userSnap.data() as User;
      if (user.isBlocked) {
        return res.status(403).json({
          success: false,
          error: { message: 'This account has been suspended due to policy violations.', code: 'USER_BLOCKED' }
        });
      }

      return res.status(200).json({
        success: true,
        data: user
      });
    } catch (error: any) {
      return res.status(500).json({
        success: false,
        error: { message: error.message, code: 'LOGIN_ERROR' }
      });
    }
  }

  static async getCurrentUser(req: AuthenticatedRequest, res: Response) {
    const uid = req.user?.uid;
    if (!uid) return res.status(401).json({ success: false, error: { message: 'Unauthorized' } });

    try {
      const doc = await collections.users.doc(uid).get();
      if (!doc.exists) return res.status(404).json({ success: false, error: { message: 'User not found' } });
      
      const user = doc.data() as User;
      if (user.isBlocked) {
        return res.status(403).json({ success: false, error: { message: 'User blocked', code: 'USER_BLOCKED' } });
      }

      return res.status(200).json({ success: true, data: user });
    } catch (e: any) {
      return res.status(500).json({ success: false, error: { message: e.message } });
    }
  }

  static async updateProfile(req: AuthenticatedRequest, res: Response) {
    const uid = req.user?.uid;
    const { displayName, photoURL } = req.body;
    if (!uid) return res.status(401).json({ success: false, error: { message: 'Unauthorized' } });

    try {
      await collections.users.doc(uid).update({
        displayName,
        photoURL,
        updatedAt: FirebaseFirestore.Timestamp.now()
      });
      return res.status(200).json({ success: true, message: 'Profile updated successfully' });
    } catch (e: any) {
      return res.status(500).json({ success: false, error: { message: e.message } });
    }
  }

  static async getReferralTree(req: AuthenticatedRequest, res: Response) {
    const uid = req.user?.uid;
    if (!uid) return res.status(401).json({ success: false, error: { message: 'Unauthorized' } });

    try {
      const snapshot = await collections.referrals
        .where('referrerUserId', '==', uid)
        .get();

      const tree: any[] = [];
      snapshot.forEach(doc => {
        tree.push(doc.data());
      });

      return res.status(200).json({ success: true, data: tree });
    } catch (e: any) {
      return res.status(500).json({ success: false, error: { message: e.message } });
    }
  }

  static async getNotifications(req: AuthenticatedRequest, res: Response) {
    const uid = req.user?.uid;
    if (!uid) return res.status(401).json({ success: false, error: { message: 'Unauthorized' } });

    try {
      const snapshot = await collections.notifications
        .where('userId', '==', uid)
        .orderBy('timestamp', 'desc')
        .limit(30)
        .get();

      const list: any[] = [];
      snapshot.forEach(doc => {
        list.push(doc.data());
      });

      return res.status(200).json({ success: true, data: list });
    } catch (e: any) {
      return res.status(500).json({ success: false, error: { message: e.message } });
    }
  }

  static async requestAccountDeletion(req: AuthenticatedRequest, res: Response) {
    const uid = req.user?.uid;
    if (!uid) return res.status(401).json({ success: false, error: { message: 'Unauthorized' } });

    try {
      const reqRef = collections.account_deletion_requests.doc(uid);
      await reqRef.set({
        id: uid,
        userId: uid,
        status: 'pending',
        requestedAt: FirebaseFirestore.Timestamp.now()
      });
      return res.status(200).json({ success: true, message: 'Account deletion requested.' });
    } catch (e: any) {
      return res.status(500).json({ success: false, error: { message: e.message } });
    }
  }

  static async logConsent(req: AuthenticatedRequest, res: Response) {
    const uid = req.user?.uid;
    const { consentType } = req.body; // terms_acceptance, privacy_consent
    if (!uid) return res.status(401).json({ success: false, error: { message: 'Unauthorized' } });

    try {
      const consentRef = collections.consent_logs.doc();
      await consentRef.set({
        id: consentRef.id,
        userId: uid,
        consentType,
        ipAddress: req.ip || 'Unknown',
        userAgent: req.headers['user-agent'] || 'Unknown',
        timestamp: FirebaseFirestore.Timestamp.now()
      });
      return res.status(201).json({ success: true, message: 'Consent logged.' });
    } catch (e: any) {
      return res.status(500).json({ success: false, error: { message: e.message } });
    }
  }

  static async logout(req: AuthenticatedRequest, res: Response) {
    // Handle clearing background sessions
    const uid = req.user?.uid;
    if (uid) {
      try {
        const sessions = await collections.user_sessions
          .where('userId', '==', uid)
          .where('active', '==', true)
          .get();
        const batch = db.batch();
        sessions.forEach(doc => {
          batch.update(doc.ref, { active: false, lastActiveAt: FirebaseFirestore.Timestamp.now() });
        });
        await batch.commit();
      } catch (e) {
        console.error('Logout error:', e);
      }
    }
    return res.status(200).json({ success: true, message: 'Logged out successfully.' });
  }
}
