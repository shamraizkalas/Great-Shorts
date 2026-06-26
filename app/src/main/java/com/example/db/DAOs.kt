package com.example.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    // User Queries
    @Query("SELECT * FROM users WHERE id = :userId LIMIT 1")
    fun getUserFlow(userId: String): Flow<UserEntity?>

    @Query("SELECT * FROM users WHERE id = :userId LIMIT 1")
    suspend fun getUserSync(userId: String): UserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    // Video Queries
    @Query("SELECT * FROM videos")
    fun getAllVideosFlow(): Flow<List<VideoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideos(videos: List<VideoEntity>)

    @Update
    suspend fun updateVideo(video: VideoEntity)

    // Watch Event Queries
    @Query("SELECT * FROM watch_events ORDER BY timestamp DESC")
    fun getAllWatchEventsFlow(): Flow<List<WatchEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWatchEvent(event: WatchEventEntity)

    // Withdrawal Queries
    @Query("SELECT * FROM withdrawals ORDER BY createdAt DESC")
    fun getAllWithdrawalsFlow(): Flow<List<WithdrawalEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWithdrawal(withdrawal: WithdrawalEntity)

    @Update
    suspend fun updateWithdrawal(withdrawal: WithdrawalEntity)

    // Bookmarks
    @Query("SELECT * FROM bookmarks WHERE userId = :userId")
    fun getBookmarksFlow(userId: String): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE userId = :userId AND videoId = :videoId")
    suspend fun deleteBookmark(userId: String, videoId: String)

    // Notifications
    @Query("SELECT * FROM notifications ORDER BY timestamp DESC")
    fun getAllNotificationsFlow(): Flow<List<NotificationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: NotificationEntity)

    @Query("UPDATE notifications SET isRead = 1 WHERE id = :notifId")
    suspend fun markNotificationAsRead(notifId: String)

    // Fraud Signals
    @Query("SELECT * FROM fraud_signals ORDER BY timestamp DESC")
    fun getAllFraudSignalsFlow(): Flow<List<FraudSignalEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFraudSignal(signal: FraudSignalEntity)

    @Update
    suspend fun updateFraudSignal(signal: FraudSignalEntity)

    // Reward Rules
    @Query("SELECT * FROM reward_rules")
    fun getAllRewardRulesFlow(): Flow<List<RewardRuleEntity>>

    @Query("SELECT * FROM reward_rules WHERE id = :id LIMIT 1")
    suspend fun getRewardRuleSync(id: String): RewardRuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRewardRules(rules: List<RewardRuleEntity>)

    // Audit Logs
    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC")
    fun getAllAuditLogsFlow(): Flow<List<AuditLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditLog(log: AuditLogEntity)
}
