import * as admin from 'firebase-admin';
import * as dotenv from 'dotenv';

dotenv.config();

// Initialize Firebase Admin SDK
// Uses service account JSON configured via environment variables or falls back to Application Default Credentials.
try {
  if (process.env.FIREBASE_SERVICE_ACCOUNT) {
    const serviceAccount = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT);
    admin.initializeApp({
      credential: admin.credential.cert(serviceAccount),
      databaseURL: process.env.FIREBASE_DATABASE_URL
    });
  } else {
    // Local emulator or implicit environment variables (e.g., App Engine, Cloud Run)
    admin.initializeApp({
      credential: admin.credential.applicationDefault(),
      databaseURL: process.env.FIREBASE_DATABASE_URL
    });
  }
  console.log('Firebase Admin initialized successfully.');
} catch (error) {
  console.error('Error initializing Firebase Admin:', error);
}

export const db = admin.firestore();
export const auth = admin.auth();

// Set collection helpers for robust typescript mapping
export const collections = {
  users: db.collection('users'),
  user_sessions: db.collection('user_sessions'),
  videos: db.collection('videos'),
  video_categories: db.collection('video_categories'),
  watch_events: db.collection('watch_events'),
  reward_rules: db.collection('reward_rules'),
  reward_ledger: db.collection('reward_ledger'),
  wallet_accounts: db.collection('wallet_accounts'),
  wallet_transactions: db.collection('wallet_transactions'),
  withdrawals: db.collection('withdrawals'),
  referrals: db.collection('referrals'),
  ad_events: db.collection('ad_events'),
  fraud_signals: db.collection('fraud_signals'),
  notifications: db.collection('notifications'),
  bookmarks: db.collection('bookmarks'),
  watch_history: db.collection('watch_history'),
  streaks: db.collection('streaks'),
  announcements: db.collection('announcements'),
  admin_actions: db.collection('admin_actions'),
  audit_logs: db.collection('audit_logs'),
  kyc_verifications: db.collection('kyc_verifications'),
  consent_logs: db.collection('consent_logs'),
  account_deletion_requests: db.collection('account_deletion_requests'),
};
