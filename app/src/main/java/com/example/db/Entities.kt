package com.example.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String = "viewer_01",
    val email: String? = "shamraizkalas@gmail.com",
    val phone: String? = "+923001234567",
    val displayName: String = "Shamraiz Kalas",
    val referralCode: String = "SHAM7788",
    val referredBy: String? = null,
    val availableBalance: Double = 120.00,
    val pendingBalance: Double = 0.00,
    val totalWithdrawn: Double = 0.00,
    val lifetimeEarned: Double = 120.00,
    val watchedMinutes: Int = 42,
    val streakDays: Int = 5,
    val accountLevel: Int = 2,
    val lastCheckInDate: String? = "2026-06-25",
    val isBlocked: Boolean = false
)

@Entity(tableName = "videos")
data class VideoEntity(
    @PrimaryKey val id: String,
    val titleEnglish: String,
    val titleUrdu: String,
    val descriptionEnglish: String,
    val descriptionUrdu: String,
    val videoUrl: String,
    val thumbnailUrl: String,
    val durationSeconds: Int,
    val categoryId: String,
    val categoryEnglish: String,
    val categoryUrdu: String,
    val viewsCount: Int,
    val likesCount: Int,
    val isPremium: Boolean = false
)

@Entity(tableName = "watch_events")
data class WatchEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: String,
    val videoId: String,
    val watchedSeconds: Int,
    val completionPercentage: Int,
    val rewardStatus: String, // "verified_credited" or "failed_fraud_held"
    val rewardAmount: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val riskScore: Int = 0
)

@Entity(tableName = "withdrawals")
data class WithdrawalEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val amount: Double,
    val method: String, // "jazzcash", "easypaisa", "bank_transfer"
    val accountNumber: String,
    val accountTitle: String,
    val bankName: String? = null,
    val status: String, // "pending", "approved", "rejected"
    val riskScore: Int,
    val adminNotes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val processedAt: Long? = null
)

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val videoId: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val titleEnglish: String,
    val titleUrdu: String,
    val messageEnglish: String,
    val messageUrdu: String,
    val type: String, // "reward_credited", "withdrawal_status", "referral_received", "announcement"
    val isRead: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "fraud_signals")
data class FraudSignalEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val signalType: String, // "abnormal_watch_speed", "emulator_fingerprint", "duplicate_session"
    val severity: String, // "low", "medium", "high"
    val riskScoreImpact: Int,
    val details: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isResolved: Boolean = false
)

@Entity(tableName = "reward_rules")
data class RewardRuleEntity(
    @PrimaryKey val id: String,
    val name: String,
    val value: Double,
    val unit: String,
    val isActive: Boolean = true
)

@Entity(tableName = "audit_logs")
data class AuditLogEntity(
    @PrimaryKey val id: String,
    val action: String,
    val details: String,
    val timestamp: Long = System.currentTimeMillis()
)
