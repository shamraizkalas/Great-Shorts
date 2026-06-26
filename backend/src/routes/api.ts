import { Router } from 'express';
import { verifyAuthToken } from '../middleware/auth';
import { verifyAdmin } from '../middleware/admin';
import { validateBody } from '../middleware/validation';
import { watchLimiter, withdrawalLimiter } from '../middleware/rateLimiter';

// Validation schemas
import {
  RegisterValidationSchema,
  ProfileUpdateValidationSchema,
  WatchStartValidationSchema,
  WatchFinishValidationSchema,
  WithdrawalRequestValidationSchema,
  RewardClaimValidationSchema,
  AdminVideoSchema,
  AdminRewardRuleSchema,
  AdminAnnouncementSchema
} from '../models/schemas';

// Controllers
import { AuthController } from '../controllers/authController';
import { VideoController } from '../controllers/videoController';
import { WatchController } from '../controllers/watchController';
import { WalletController } from '../controllers/walletController';
import { WithdrawalController } from '../controllers/withdrawalController';
import { AdminController } from '../controllers/adminController';

const router = Router();

// --- PUBLIC AUTH / INITIALIZATION ROUTES ---
router.post('/auth/register', validateBody(RegisterValidationSchema), AuthController.register);
router.post('/auth/login', AuthController.login); // Standard token check or verification setup

// --- AUTHENTICATED ROUTES (Bearer Token required) ---
router.use(verifyAuthToken);

router.post('/auth/logout', AuthController.logout);
router.get('/me', AuthController.getCurrentUser);
router.patch('/me/profile', validateBody(ProfileUpdateValidationSchema), AuthController.updateProfile);

// --- VIDEO ROUTES ---
router.get('/videos', VideoController.getVideos);
router.get('/videos/:id', VideoController.getVideoById);
router.post('/bookmarks', VideoController.addBookmark);
router.delete('/bookmarks/:id', VideoController.removeBookmark);

// --- WATCH & REWARD EVENTS (WITH RATE LIMITS) ---
router.post('/watch/start', watchLimiter, validateBody(WatchStartValidationSchema), WatchController.startWatchSession);
router.post('/watch/finish', watchLimiter, validateBody(WatchFinishValidationSchema), WatchController.finishWatchSession);
router.post('/reward/claim', validateBody(RewardClaimValidationSchema), WatchController.claimBonus);
router.post('/streaks/check-in', WatchController.dailyStreakCheckIn);

// --- WALLET & WITHDRAWAL ROUTES ---
router.get('/wallet', WalletController.getWallet);
router.get('/wallet/transactions', WalletController.getTransactions);
router.get('/withdrawals', WithdrawalController.getWithdrawalHistory);
router.post('/withdrawals/request', withdrawalLimiter, validateBody(WithdrawalRequestValidationSchema), WithdrawalController.requestWithdrawal);

// --- USER RETENTION & COMPLIANCE ---
router.get('/referrals', AuthController.getReferralTree);
router.get('/notifications', AuthController.getNotifications);
router.post('/compliance/delete-account', AuthController.requestAccountDeletion);
router.post('/compliance/consent', AuthController.logConsent);

// --- ADMINISTRATIVE PORTAL (Admin claiming verification required) ---
router.use(verifyAdmin);

router.post('/admin/videos', validateBody(AdminVideoSchema), AdminController.addVideo);
router.patch('/admin/videos/:id', validateBody(AdminVideoSchema), AdminController.updateVideo);
router.post('/admin/reward-rules/:id', validateBody(AdminRewardRuleSchema), AdminController.updateRewardRule);
router.get('/admin/withdrawals', AdminController.getWithdrawalRequests);
router.post('/admin/withdrawals/:id/approve', AdminController.approveWithdrawal);
router.post('/admin/withdrawals/:id/reject', AdminController.rejectWithdrawal);
router.post('/admin/users/:id/block', AdminController.blockUser);
router.post('/admin/users/:id/unblock', AdminController.unblockUser);
router.post('/admin/announcements', validateBody(AdminAnnouncementSchema), AdminController.createAnnouncement);
router.get('/admin/fraud-signals', AdminController.getFraudSignals);
router.get('/admin/audit-logs', AdminController.getAuditLogs);

export default router;
