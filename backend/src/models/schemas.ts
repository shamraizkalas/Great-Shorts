import Joi from 'joi';

// TypeScript Interfaces for all collections

export interface User {
  id: string;
  email?: string;
  phone?: string;
  displayName?: string;
  photoURL?: string;
  role: 'user' | 'admin';
  createdAt: FirebaseFirestore.Timestamp;
  updatedAt: FirebaseFirestore.Timestamp;
  isBlocked: boolean;
  status: 'active' | 'blocked';
  referralCode: string; // Unique referral code
  referredBy?: string; // Referral code of user who invited this user
  accountLevel: number; // For progression systems
}

export interface UserSession {
  id: string;
  userId: string;
  deviceId: string;
  ipAddress: string;
  userAgent: string;
  active: boolean;
  createdAt: FirebaseFirestore.Timestamp;
  lastActiveAt: FirebaseFirestore.Timestamp;
}

export interface Video {
  id: string;
  title: string;
  description: string;
  videoUrl: string;
  thumbnailUrl: string;
  durationSeconds: number;
  categoryId: string;
  isPremium: boolean;
  isSoftDeleted: boolean;
  createdAt: FirebaseFirestore.Timestamp;
  viewsCount: number;
  likesCount: number;
}

export interface VideoCategory {
  id: string;
  nameEnglish: string;
  nameUrdu: string;
  slug: string;
  createdAt: FirebaseFirestore.Timestamp;
}

export interface WatchEvent {
  id: string;
  userId: string;
  videoId: string;
  sessionId: string;
  startTime: FirebaseFirestore.Timestamp;
  endTime: FirebaseFirestore.Timestamp;
  watchedSeconds: number;
  completionPercentage: number;
  deviceId: string;
  ipAddress: string;
  userAgent: string;
  rewardStatus: 'pending' | 'verified_credited' | 'failed_fraud_held';
  rewardAmount: number; // Amount credited in PKR or points
}

export interface RewardRule {
  id: string; // e.g. 'daily_limit', 'watch_pkr_per_sec', 'streak_bonus_day_3'
  name: string;
  description: string;
  value: number; // Parameter value
  unit: 'pkr' | 'seconds' | 'days' | 'percentage';
  isActive: boolean;
  updatedAt: FirebaseFirestore.Timestamp;
  updatedBy: string; // Admin userId
}

export interface RewardLedger {
  id: string;
  userId: string;
  amount: number;
  type: 'watch_bonus' | 'daily_check_in' | 'streak_bonus' | 'referral_bonus' | 'ad_bonus' | 'admin_adjustment';
  referenceId: string; // ID of watch_event, referral, streak, etc.
  timestamp: FirebaseFirestore.Timestamp;
  description: string;
}

export interface WalletAccount {
  userId: string;
  availableBalance: number;
  pendingBalance: number;
  lifetimeEarned: number;
  totalWithdrawn: number;
  updatedAt: FirebaseFirestore.Timestamp;
}

export interface WalletTransaction {
  id: string;
  userId: string;
  amount: number;
  type: 'credit_earning' | 'debit_withdrawal' | 'adjustment_hold';
  status: 'completed' | 'pending' | 'reversed';
  referenceId: string; // Ledger entry ID or Withdrawal ID
  timestamp: FirebaseFirestore.Timestamp;
  description: string;
}

export interface Withdrawal {
  id: string;
  userId: string;
  amount: number;
  method: 'jazzcash' | 'easypaisa' | 'bank_transfer';
  accountDetails: {
    accountNumber: string;
    accountTitle: string;
    bankName?: string;
  };
  status: 'pending' | 'approved' | 'rejected';
  riskScore: number; // Calculated by fraud service
  adminNotes?: string;
  createdAt: FirebaseFirestore.Timestamp;
  processedAt?: FirebaseFirestore.Timestamp;
  processedBy?: string; // Admin userId
}

export interface Referral {
  id: string;
  referrerUserId: string;
  referredUserId: string;
  referralCodeUsed: string;
  status: 'joined' | 'qualified'; // 'qualified' after first valid watch event
  bonusAmount: number;
  timestamp: FirebaseFirestore.Timestamp;
  qualifiedTimestamp?: FirebaseFirestore.Timestamp;
}

export interface AdEvent {
  id: string;
  userId: string;
  adProvider: string; // 'admob', 'unity'
  adUnitId: string;
  status: 'watched' | 'clicked';
  rewardAmount: number;
  timestamp: FirebaseFirestore.Timestamp;
}

export interface FraudSignal {
  id: string;
  userId: string;
  sessionId?: string;
  watchEventId?: string;
  signalType: 'abnormal_watch_speed' | 'duplicate_session' | 'emulator_fingerprint' | 'bot_touch_patterns';
  severity: 'low' | 'medium' | 'high';
  riskScoreImpact: number;
  details: string;
  timestamp: FirebaseFirestore.Timestamp;
  isResolved: boolean;
}

export interface Notification {
  id: string;
  userId: string;
  title: string;
  message: string;
  type: 'reward_credited' | 'withdrawal_status' | 'referral_received' | 'announcement';
  isRead: boolean;
  timestamp: FirebaseFirestore.Timestamp;
}

export interface Bookmark {
  id: string;
  userId: string;
  videoId: string;
  timestamp: FirebaseFirestore.Timestamp;
}

export interface WatchHistory {
  id: string;
  userId: string;
  videoId: string;
  watchedSeconds: number;
  lastWatchedAt: FirebaseFirestore.Timestamp;
}

export interface Streak {
  userId: string;
  currentStreakDays: number;
  lastCheckInAt: FirebaseFirestore.Timestamp;
  streakHistory: string[]; // List of date strings "YYYY-MM-DD"
}

export interface Announcement {
  id: string;
  titleEnglish: string;
  titleUrdu: string;
  contentEnglish: string;
  contentUrdu: string;
  createdAt: FirebaseFirestore.Timestamp;
  active: boolean;
}

export interface AuditLog {
  id: string;
  adminUserId: string;
  action: 'block_user' | 'unblock_user' | 'approve_withdrawal' | 'reject_withdrawal' | 'update_reward_rules' | 'manual_balance_adjustment' | 'add_video';
  targetId: string; // e.g. target userId, target withdrawalId
  details: string;
  timestamp: FirebaseFirestore.Timestamp;
}

export interface KycVerification {
  userId: string;
  cnicNumber: string;
  cnicFrontUrl?: string;
  cnicBackUrl?: string;
  status: 'pending' | 'verified' | 'rejected';
  submittedAt: FirebaseFirestore.Timestamp;
  reviewedAt?: FirebaseFirestore.Timestamp;
  reviewedBy?: string;
}

export interface ConsentLog {
  id: string;
  userId: string;
  consentType: 'terms_acceptance' | 'privacy_consent';
  ipAddress: string;
  userAgent: string;
  timestamp: FirebaseFirestore.Timestamp;
}

export interface AccountDeletionRequest {
  id: string;
  userId: string;
  status: 'pending' | 'cancelled' | 'processed';
  requestedAt: FirebaseFirestore.Timestamp;
  processedAt?: FirebaseFirestore.Timestamp;
}


// --- Joi Validation Schemas for API Requests ---

export const RegisterValidationSchema = Joi.object({
  email: Joi.string().email().optional(),
  phone: Joi.string().pattern(/^\+?92[0-9]{9,10}$/).optional(), // Pakistan format e.g. +923001234567
  displayName: Joi.string().min(3).max(50).required(),
  referralCode: Joi.string().alphanum().length(8).optional(), // Referral code of person who referred this user
});

export const ProfileUpdateValidationSchema = Joi.object({
  displayName: Joi.string().min(3).max(50).optional(),
  photoURL: Joi.string().uri().optional(),
});

export const WatchStartValidationSchema = Joi.object({
  videoId: Joi.string().required(),
  sessionId: Joi.string().required(),
  deviceId: Joi.string().required(),
});

export const WatchFinishValidationSchema = Joi.object({
  videoId: Joi.string().required(),
  sessionId: Joi.string().required(),
  watchedSeconds: Joi.number().min(1).max(3600).required(),
  completionPercentage: Joi.number().min(0).max(100).required(),
  deviceId: Joi.string().required(),
  fingerprint: Joi.object({
    isEmulator: Joi.boolean().required(),
    osVersion: Joi.string().required(),
    hardware: Joi.string().required(),
  }).required(),
});

export const WithdrawalRequestValidationSchema = Joi.object({
  amount: Joi.number().min(100).required(), // min limit e.g. 100 PKR
  method: Joi.string().valid('jazzcash', 'easypaisa', 'bank_transfer').required(),
  accountNumber: Joi.string().min(10).max(30).required(),
  accountTitle: Joi.string().min(3).max(60).required(),
  bankName: Joi.string().optional().when('method', {
    is: 'bank_transfer',
    then: Joi.required(),
  }),
});

export const RewardClaimValidationSchema = Joi.object({
  claimType: Joi.string().valid('daily_check_in', 'streak_bonus').required(),
});

export const SupportTicketValidationSchema = Joi.object({
  subject: Joi.string().min(5).max(100).required(),
  message: Joi.string().min(10).max(1000).required(),
});

export const AdminVideoSchema = Joi.object({
  title: Joi.string().required(),
  description: Joi.string().required(),
  videoUrl: Joi.string().uri().required(),
  thumbnailUrl: Joi.string().uri().required(),
  durationSeconds: Joi.number().min(5).required(),
  categoryId: Joi.string().required(),
  isPremium: Joi.boolean().default(false),
});

export const AdminRewardRuleSchema = Joi.object({
  value: Joi.number().min(0).required(),
  isActive: Joi.boolean().required(),
});

export const AdminAnnouncementSchema = Joi.object({
  titleEnglish: Joi.string().required(),
  titleUrdu: Joi.string().required(),
  contentEnglish: Joi.string().required(),
  contentUrdu: Joi.string().required(),
});
