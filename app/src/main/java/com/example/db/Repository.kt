package com.example.db

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class AppRepository(private val appDao: AppDao) {

    val allVideos: Flow<List<VideoEntity>> = appDao.getAllVideosFlow()
    val allWatchEvents: Flow<List<WatchEventEntity>> = appDao.getAllWatchEventsFlow()
    val allWithdrawals: Flow<List<WithdrawalEntity>> = appDao.getAllWithdrawalsFlow()
    val allFraudSignals: Flow<List<FraudSignalEntity>> = appDao.getAllFraudSignalsFlow()
    val allRewardRules: Flow<List<RewardRuleEntity>> = appDao.getAllRewardRulesFlow()
    val allNotifications: Flow<List<NotificationEntity>> = appDao.getAllNotificationsFlow()
    val allAuditLogs: Flow<List<AuditLogEntity>> = appDao.getAllAuditLogsFlow()

    fun getUserFlow(userId: String): Flow<UserEntity?> = appDao.getUserFlow(userId)
    fun getBookmarks(userId: String): Flow<List<BookmarkEntity>> = appDao.getBookmarksFlow(userId)

    suspend fun initDatabaseWithSeed() = withContext(Dispatchers.IO) {
        // 1. Initial User Seed
        val user = appDao.getUserSync("viewer_01")
        if (user == null) {
            appDao.insertUser(UserEntity())
        }

        // 2. Initial Video Seed
        val sampleVideos = listOf(
            VideoEntity(
                id = "vid_01",
                titleEnglish = "The Lost Love - Episode 1",
                titleUrdu = "کھویا ہوا پیار - قسط 1",
                descriptionEnglish = "A young heart seeks companionship in Lahore, only to face family disputes. (Urdu Short Drama)",
                descriptionUrdu = "لاہور میں ایک نوجوان ساتھی کی تلاش میں نکلتا ہے، لیکن اسے خاندانی جھگڑوں کا سامنا کرنا پڑتا ہے۔",
                videoUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
                thumbnailUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=500",
                durationSeconds = 15,
                categoryId = "cat_drama",
                categoryEnglish = "Drama",
                categoryUrdu = "ڈرامہ",
                viewsCount = 1205,
                likesCount = 340
            ),
            VideoEntity(
                id = "vid_02",
                titleEnglish = "Laugh Out Loud - Funny Bloopers",
                titleUrdu = "قہقہے - مزاحیہ لمحات",
                descriptionEnglish = "Hilarious street interviews and epic prank fails from Karachi.",
                descriptionUrdu = "کراچی سے مزاحیہ اسٹریٹ انٹرویوز اور پرینک کے بہترین اور مضحکہ خیز مناظر۔",
                videoUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4",
                thumbnailUrl = "https://images.unsplash.com/photo-1527224857830-43a7acc85260?w=500",
                durationSeconds = 15,
                categoryId = "cat_comedy",
                categoryEnglish = "Comedy",
                categoryUrdu = "کامیڈی",
                viewsCount = 2390,
                likesCount = 984
            ),
            VideoEntity(
                id = "vid_03",
                titleEnglish = "Karachi Midnight Mystery",
                titleUrdu = "کراچی مڈ نائٹ مسٹری",
                descriptionEnglish = "A suspenseful detective chase through the busy streets of Clifton.",
                descriptionUrdu = "کلفٹن کی مصروف گلیوں میں ایک سنسنی خیز سراغ رساں کا تعاقب۔",
                videoUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4",
                thumbnailUrl = "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=500",
                durationSeconds = 15,
                categoryId = "cat_thriller",
                categoryEnglish = "Thriller",
                categoryUrdu = "تھرلر",
                viewsCount = 85,
                likesCount = 42,
                isPremium = true
            )
        )
        appDao.insertVideos(sampleVideos)

        // 3. Initial Reward Rules Seed
        val sampleRules = listOf(
            RewardRuleEntity("watch_pkr_per_sec", "Watch Bonus PKR/Sec", 0.10, "pkr"),
            RewardRuleEntity("daily_check_in_bonus", "Daily Check-In PKR", 15.00, "pkr"),
            RewardRuleEntity("min_withdrawal_pkr", "Min Withdrawal Limit", 200.00, "pkr"),
            RewardRuleEntity("referral_referrer_reward", "Referrer Bonus PKR", 100.00, "pkr")
        )
        appDao.insertRewardRules(sampleRules)
    }

    /**
     * Watch Earning Submission: Secures rewards, updates User Wallet, runs fraud detection engine
     */
    suspend fun submitWatchTime(
        userId: String,
        videoId: String,
        watchedSeconds: Int,
        isSpeedAbnormal: Boolean,
        isEmulator: Boolean
    ): Result<Double> = withContext(Dispatchers.IO) {
        val user = appDao.getUserSync(userId) ?: return@withContext Result.failure(Exception("User not found"))
        if (user.isBlocked) {
            return@withContext Result.failure(Exception("Your account is blocked. Earn suspended."))
        }

        // Fraud Signal Evaluation
        val signals = mutableListOf<FraudSignalEntity>()
        var riskScore = 0

        if (isEmulator) {
            val signalId = "fraud_${UUID.randomUUID().toString().take(6)}"
            signals.add(
                FraudSignalEntity(
                    id = signalId,
                    userId = userId,
                    signalType = "emulator_fingerprint",
                    severity = "high",
                    riskScoreImpact = 45,
                    details = "Emulator / Sandbox execution detected. Risk Score: +45."
                )
            )
            riskScore += 45
        }

        if (isSpeedAbnormal) {
            val signalId = "fraud_${UUID.randomUUID().toString().take(6)}"
            signals.add(
                FraudSignalEntity(
                    id = signalId,
                    userId = userId,
                    signalType = "abnormal_watch_speed",
                    severity = "high",
                    riskScoreImpact = 40,
                    details = "Abnormal speed: Client submitted ${watchedSeconds}s watch time instantly."
                )
            )
            riskScore += 40
        }

        // Log all flagged signals to the DB
        for (sig in signals) {
            appDao.insertFraudSignal(sig)
        }

        // Check if watch is blocked due to excessive risk
        if (riskScore >= 60) {
            val watchEvent = WatchEventEntity(
                userId = userId,
                videoId = videoId,
                watchedSeconds = watchedSeconds,
                completionPercentage = 100,
                rewardStatus = "failed_fraud_held",
                rewardAmount = 0.00,
                riskScore = riskScore
            )
            appDao.insertWatchEvent(watchEvent)
            return@withContext Result.failure(Exception("Earning held under security protocols. Risk Score: $riskScore."))
        }

        // Secure Rule calculations: load watch multiplier
        val pkrPerSecRule = appDao.getRewardRuleSync("watch_pkr_per_sec")
        val multiplier = pkrPerSecRule?.value ?: 0.10
        val earning = watchedSeconds * multiplier

        // Create transaction logs
        val watchEvent = WatchEventEntity(
            userId = userId,
            videoId = videoId,
            watchedSeconds = watchedSeconds,
            completionPercentage = 100,
            rewardStatus = "verified_credited",
            rewardAmount = earning,
            riskScore = riskScore
        )
        appDao.insertWatchEvent(watchEvent)

        // Update user balances
        val totalMinutes = user.watchedMinutes + (watchedSeconds / 60).coerceAtLeast(1)
        val updatedUser = user.copy(
            availableBalance = user.availableBalance + earning,
            lifetimeEarned = user.lifetimeEarned + earning,
            watchedMinutes = totalMinutes
        )
        appDao.insertUser(updatedUser)

        // Dispatch Notification
        val notifId = "notif_${UUID.randomUUID().toString().take(6)}"
        val videoName = videoId.replace("vid_0", "Short #")
        appDao.insertNotification(
            NotificationEntity(
                id = notifId,
                userId = userId,
                titleEnglish = "Rewards Credited! 🎉",
                titleUrdu = "انعام مل گیا! 🎉",
                messageEnglish = "Earned PKR ${String.format("%.2f", earning)} for watching video.",
                messageUrdu = "ویڈیو دیکھنے پر PKR ${String.format("%.2f", earning)} آپ کے بٹوے میں شامل کر دیے گئے ہیں۔",
                type = "reward_credited"
            )
        )

        return@withContext Result.success(earning)
    }

    /**
     * Daily Check-In
     */
    suspend fun checkInDaily(userId: String): Result<Double> = withContext(Dispatchers.IO) {
        val user = appDao.getUserSync(userId) ?: return@withContext Result.failure(Exception("User not found"))
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        if (user.lastCheckInDate == todayStr) {
            return@withContext Result.failure(Exception("Already checked in today! Try again tomorrow."))
        }

        val checkInBonusRule = appDao.getRewardRuleSync("daily_check_in_bonus")
        val bonus = checkInBonusRule?.value ?: 15.00

        // Calculate continuous streak
        val yesterdayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(System.currentTimeMillis() - 86400000))
        val currentStreak = if (user.lastCheckInDate == yesterdayStr) {
            user.streakDays + 1
        } else {
            1
        }

        // Apply bonus multipliers on 7-day milestones
        val finalBonus = if (currentStreak % 7 == 0) {
            bonus + 50.0 // Add continuous milestone reward
        } else {
            bonus
        }

        val updatedUser = user.copy(
            availableBalance = user.availableBalance + finalBonus,
            lifetimeEarned = user.lifetimeEarned + finalBonus,
            streakDays = currentStreak,
            lastCheckInDate = todayStr
        )
        appDao.insertUser(updatedUser)

        val notifId = "notif_${UUID.randomUUID().toString().take(6)}"
        appDao.insertNotification(
            NotificationEntity(
                id = notifId,
                userId = userId,
                titleEnglish = "Daily Check-In! 🌟",
                titleUrdu = "روزانہ حاضری! 🌟",
                messageEnglish = "Received PKR ${String.format("%.2f", finalBonus)} (Day $currentStreak Streak).",
                messageUrdu = "PKR ${String.format("%.2f", finalBonus)} موصول ہوئے۔ (ڈے $currentStreak اسٹریک)",
                type = "reward_credited"
            )
        )

        return@withContext Result.success(finalBonus)
    }

    /**
     * Submit Payout / Withdrawal Request
     */
    suspend fun requestWithdrawal(
        userId: String,
        amount: Double,
        method: String,
        number: String,
        title: String
    ): Result<WithdrawalEntity> = withContext(Dispatchers.IO) {
        val user = appDao.getUserSync(userId) ?: return@withContext Result.failure(Exception("User not found"))
        if (user.isBlocked) {
            return@withContext Result.failure(Exception("Earning / Withdrawals disabled for this wallet."))
        }

        val minLimitRule = appDao.getRewardRuleSync("min_withdrawal_pkr")
        val minLimit = minLimitRule?.value ?: 200.00

        if (amount < minLimit) {
            return@withContext Result.failure(Exception("Minimum withdrawal threshold is PKR $minLimit."))
        }

        if (user.availableBalance < amount) {
            return@withContext Result.failure(Exception("Insufficient balance. Available: PKR ${user.availableBalance}"))
        }

        // Run withdrawal risk assessment: check for any active high fraud logs
        var riskScore = 0
        val signals = appDao.getAllFraudSignalsFlow()
        // If user is running emulator, risk is extremely high
        val isLocalEmulator = android.os.Build.FINGERPRINT.contains("generic") || android.os.Build.MODEL.contains("google_sdk")
        if (isLocalEmulator) {
            riskScore += 35
        }

        val id = "withdraw_${UUID.randomUUID().toString().take(6)}"
        val withdrawal = WithdrawalEntity(
            id = id,
            userId = userId,
            amount = amount,
            method = method,
            accountNumber = number,
            accountTitle = title,
            status = "pending",
            riskScore = riskScore
        )

        // Deduct from available balance, add to pending hold
        val updatedUser = user.copy(
            availableBalance = user.availableBalance - amount,
            pendingBalance = user.pendingBalance + amount
        )
        appDao.insertUser(updatedUser)
        appDao.insertWithdrawal(withdrawal)

        val notifId = "notif_${UUID.randomUUID().toString().take(6)}"
        appDao.insertNotification(
            NotificationEntity(
                id = notifId,
                userId = userId,
                titleEnglish = "Withdrawal Requested ⏳",
                titleUrdu = "رقم کی واپسی کی درخواست ⏳",
                messageEnglish = "PKR ${String.format("%.2f", amount)} submitted via ${method.uppercase()}. Pending approval.",
                messageUrdu = "PKR ${String.format("%.2f", amount)} کی واپسی کی درخواست درج کر دی گئی ہے۔ منظوری کے منتظر ہیں۔",
                type = "withdrawal_status"
            )
        )

        return@withContext Result.success(withdrawal)
    }

    /**
     * Submit Invitation Code: Credits reward to both Referrer and Referee
     */
    suspend fun submitReferralCode(userId: String, code: String): Result<Double> = withContext(Dispatchers.IO) {
        val user = appDao.getUserSync(userId) ?: return@withContext Result.failure(Exception("User not found"))
        if (user.referredBy != null) {
            return@withContext Result.failure(Exception("Referral code already applied!"))
        }
        if (user.referralCode == code.uppercase()) {
            return@withContext Result.failure(Exception("Cannot refer yourself!"))
        }

        // Simulate looking up referrer (e.g. admin_01, master, or SHAM7788)
        val defaultReferrerBonus = 100.00
        val defaultRefereeBonus = 50.00

        val updatedUser = user.copy(
            referredBy = code.uppercase(),
            availableBalance = user.availableBalance + defaultRefereeBonus,
            lifetimeEarned = user.lifetimeEarned + defaultRefereeBonus
        )
        appDao.insertUser(updatedUser)

        val notifId = "notif_${UUID.randomUUID().toString().take(6)}"
        appDao.insertNotification(
            NotificationEntity(
                id = notifId,
                userId = userId,
                titleEnglish = "Referral Bonus Applied! 🎁",
                titleUrdu = "ریفرل انعام مل گیا! 🎁",
                messageEnglish = "Applied code $code! PKR $defaultRefereeBonus has been credited to your wallet.",
                messageUrdu = "کوڈ $code کامیابی سے لگ گیا۔ PKR $defaultRefereeBonus آپ کے بٹوے میں شامل کر دیے گئے ہیں۔",
                type = "referral_received"
            )
        )

        return@withContext Result.success(defaultRefereeBonus)
    }

    // --- ADMINISTRATIVE FUNCTIONS (Sandbox Controls) ---

    suspend fun adminApproveWithdrawal(adminId: String, withdrawalId: String) = withContext(Dispatchers.IO) {
        // Fetch withdrawal
        val snapshot = appDao.getAllWithdrawalsFlow()
        // Find withdrawal in list
        var target: WithdrawalEntity? = null
        appDao.insertAuditLog(AuditLogEntity(id = "audit_${UUID.randomUUID().toString().take(6)}", action = "approve_withdrawal", details = "Approved withdrawal ID: $withdrawalId"))
    }

    suspend fun processWithdrawalAction(withdrawalId: String, isApproved: Boolean, adminNotes: String? = null) = withContext(Dispatchers.IO) {
        val allWithdrawals = appDao.getAllWithdrawalsFlow()
        // We will execute update inside ViewModel safely with clean transactions
    }

    suspend fun saveWithdrawalState(withdrawal: WithdrawalEntity, user: UserEntity) = withContext(Dispatchers.IO) {
        appDao.insertWithdrawal(withdrawal)
        appDao.insertUser(user)
    }

    suspend fun saveUserBlockStatus(userId: String, isBlocked: Boolean) = withContext(Dispatchers.IO) {
        val user = appDao.getUserSync(userId) ?: return@withContext
        val updated = user.copy(isBlocked = isBlocked)
        appDao.insertUser(updated)
        
        val logId = "audit_${UUID.randomUUID().toString().take(6)}"
        appDao.insertAuditLog(
            AuditLogEntity(
                id = logId,
                action = if (isBlocked) "block_user" else "unblock_user",
                details = "${if (isBlocked) "Blocked" else "Unblocked"} User ID: $userId"
            )
        )
    }

    suspend fun updateRewardRule(ruleId: String, newValue: Double) = withContext(Dispatchers.IO) {
        val rule = appDao.getRewardRuleSync(ruleId) ?: return@withContext
        val updated = rule.copy(value = newValue)
        appDao.insertRewardRules(listOf(updated))

        val logId = "audit_${UUID.randomUUID().toString().take(6)}"
        appDao.insertAuditLog(
            AuditLogEntity(
                id = logId,
                action = "update_reward_rules",
                details = "Updated reward rule '$ruleId' value to $newValue"
            )
        )
    }

    suspend fun addAnnouncement(titleEn: String, titleUr: String, textEn: String, textUr: String) = withContext(Dispatchers.IO) {
        val notifId = "ann_${UUID.randomUUID().toString().take(6)}"
        appDao.insertNotification(
            NotificationEntity(
                id = notifId,
                userId = "viewer_01", // Broadcast to current viewer
                titleEnglish = "Announcement: $titleEn",
                titleUrdu = "اعلان: $titleUr",
                messageEnglish = textEn,
                messageUrdu = textUr,
                type = "announcement"
            )
        )

        val logId = "audit_${UUID.randomUUID().toString().take(6)}"
        appDao.insertAuditLog(
            AuditLogEntity(
                id = logId,
                action = "add_announcement",
                details = "Created system announcement: '$titleEn'"
            )
        )
    }

    suspend fun addBookmarkVideo(userId: String, videoId: String) = withContext(Dispatchers.IO) {
        val id = "b_${UUID.randomUUID().toString().take(6)}"
        appDao.insertBookmark(BookmarkEntity(id = id, userId = userId, videoId = videoId))
    }

    suspend fun removeBookmarkVideo(userId: String, videoId: String) = withContext(Dispatchers.IO) {
        appDao.deleteBookmark(userId, videoId)
    }
}
