package com.example.db

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

enum class AppTab {
    FEED, WALLET, REFERRALS, INBOX, PROFILE, ADMIN
}

enum class Language {
    ENGLISH, URDU
}

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = AppRepository(db.appDao())

    // Language Toggle State
    private val _language = MutableStateFlow(Language.ENGLISH)
    val language: StateFlow<Language> = _language.asStateFlow()

    // Tab Navigation State
    private val _activeTab = MutableStateFlow(AppTab.FEED)
    val activeTab: StateFlow<AppTab> = _activeTab.asStateFlow()

    // Flow states from DB Room models
    val userFlow: StateFlow<UserEntity?> = repository.getUserFlow("viewer_01")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val videos: StateFlow<List<VideoEntity>> = repository.allVideos
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val watchEvents: StateFlow<List<WatchEventEntity>> = repository.allWatchEvents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val withdrawals: StateFlow<List<WithdrawalEntity>> = repository.allWithdrawals
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val fraudSignals: StateFlow<List<FraudSignalEntity>> = repository.allFraudSignals
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val rewardRules: StateFlow<List<RewardRuleEntity>> = repository.allRewardRules
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notifications: StateFlow<List<NotificationEntity>> = repository.allNotifications
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val auditLogs: StateFlow<List<AuditLogEntity>> = repository.allAuditLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bookmarkedVideos: StateFlow<List<BookmarkEntity>> = repository.getBookmarks("viewer_01")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active playing video state
    private val _currentPlayingVideoIndex = MutableStateFlow(0)
    val currentPlayingVideoIndex: StateFlow<Int> = _currentPlayingVideoIndex.asStateFlow()

    // Status Message triggers
    private val _toastMessageEn = MutableStateFlow<String?>(null)
    val toastMessageEn: StateFlow<String?> = _toastMessageEn.asStateFlow()

    private val _toastMessageUr = MutableStateFlow<String?>(null)
    val toastMessageUr: StateFlow<String?> = _toastMessageUr.asStateFlow()

    init {
        viewModelScope.launch {
            // Seed database on launch
            repository.initDatabaseWithSeed()
        }
    }

    fun toggleLanguage() {
        _language.value = if (_language.value == Language.ENGLISH) Language.URDU else Language.ENGLISH
    }

    fun setTab(tab: AppTab) {
        _activeTab.value = tab
    }

    fun selectNextVideo() {
        val size = videos.value.size
        if (size > 0) {
            _currentPlayingVideoIndex.value = (_currentPlayingVideoIndex.value + 1) % size
        }
    }

    fun selectPreviousVideo() {
        val size = videos.value.size
        if (size > 0) {
            _currentPlayingVideoIndex.value = (_currentPlayingVideoIndex.value - 1 + size) % size
        }
    }

    fun showToast(en: String, ur: String) {
        _toastMessageEn.value = en
        _toastMessageUr.value = ur
    }

    fun clearToast() {
        _toastMessageEn.value = null
        _toastMessageUr.value = null
    }

    /**
     * User Action: Daily Check-In
     */
    fun performDailyCheckIn() {
        viewModelScope.launch {
            repository.checkInDaily("viewer_01").onSuccess { earned ->
                showToast(
                    "Checked in! Earned PKR ${String.format("%.2f", earned)}.",
                    "حاضری لگ گئی! PKR ${String.format("%.2f", earned)} مل گئے۔"
                )
            }.onFailure { err ->
                showToast(
                    err.message ?: "Failed check-in",
                    "حاضری ناکام: پہلے سے حاصل شدہ"
                )
            }
        }
    }

    /**
     * User Action: Apply Referral Code
     */
    fun applyReferral(code: String) {
        if (code.isBlank()) return
        viewModelScope.launch {
            repository.submitReferralCode("viewer_01", code).onSuccess { reward ->
                showToast(
                    "Referral applied! Received PKR ${String.format("%.2f", reward)}.",
                    "ریفرل کوڈ لاگو! PKR ${String.format("%.2f", reward)} بٹوے میں شامل۔"
                )
            }.onFailure { err ->
                showToast(
                    err.message ?: "Referral failed",
                    "ریفرل کوڈ ناکام: غلط یا پہلے سے لاگو"
                )
            }
        }
    }

    /**
     * User Action: Submit Withdrawal Payout request
     */
    fun requestWithdraw(amount: Double, method: String, number: String, title: String) {
        viewModelScope.launch {
            repository.requestWithdrawal("viewer_01", amount, method, number, title).onSuccess {
                showToast(
                    "Withdrawal request of PKR ${String.format("%.2f", amount)} submitted successfully!",
                    "رقم کی واپسی کی درخواست کامیابی کے ساتھ درج کر دی گئی ہے!"
                )
            }.onFailure { err ->
                showToast(
                    err.message ?: "Withdrawal failed",
                    "درخواست ناکام: ${err.message}"
                )
            }
        }
    }

    /**
     * Simulation Action: Records video watched seconds and triggers secure backend check
     */
    fun submitVideoWatchTime(videoId: String, seconds: Int, simulateAbnormalSpeed: Boolean, simulateEmulator: Boolean) {
        viewModelScope.launch {
            repository.submitWatchTime(
                userId = "viewer_01",
                videoId = videoId,
                watchedSeconds = seconds,
                isSpeedAbnormal = simulateAbnormalSpeed,
                isEmulator = simulateEmulator
            ).onSuccess { earning ->
                showToast(
                    "Verified & Credited: +PKR ${String.format("%.2f", earning)}!",
                    "تصدیق شدہ اور اکاؤنٹ میں شامل: +PKR ${String.format("%.2f", earning)}!"
                )
            }.onFailure { err ->
                showToast(
                    err.message ?: "Watch time held",
                    "سیکورٹی الرٹ: ${err.message}"
                )
            }
        }
    }

    /**
     * Video Bookmark Toggle
     */
    fun toggleBookmark(videoId: String) {
        viewModelScope.launch {
            val isBookmarked = bookmarkedVideos.value.any { it.videoId == videoId }
            if (isBookmarked) {
                repository.removeBookmarkVideo("viewer_01", videoId)
                showToast("Removed from bookmarks", "بک مارکس سے ہٹا دیا گیا")
            } else {
                repository.addBookmarkVideo("viewer_01", videoId)
                showToast("Added to bookmarks", "بک مارکس میں شامل کر دیا گیا")
            }
        }
    }

    // --- ADMINISTRATIVE ACTIONS (SANDBOX SIMULATIONS) ---

    fun adminApproveWithdrawal(withdrawalId: String) {
        viewModelScope.launch {
            val target = withdrawals.value.find { it.id == withdrawalId } ?: return@launch
            val user = userFlow.value ?: return@launch

            // Transition payout: subtract pending hold, add to totalWithdrawn
            val updatedUser = user.copy(
                pendingBalance = (user.pendingBalance - target.amount).coerceAtLeast(0.0),
                totalWithdrawn = user.totalWithdrawn + target.amount
            )

            val updatedWithdrawal = target.copy(
                status = "approved",
                processedAt = System.currentTimeMillis()
            )

            repository.saveWithdrawalState(updatedWithdrawal, updatedUser)

            // Insert audit log
            val logId = "audit_${UUID.randomUUID().toString().take(6)}"
            db.appDao().insertAuditLog(
                AuditLogEntity(
                    id = logId,
                    action = "approve_withdrawal",
                    details = "Approved withdrawal ID: $withdrawalId (PKR ${target.amount})"
                )
            )

            // Dispatch notification
            val notifId = "notif_${UUID.randomUUID().toString().take(6)}"
            db.appDao().insertNotification(
                NotificationEntity(
                    id = notifId,
                    userId = "viewer_01",
                    titleEnglish = "Withdrawal Dispatched! 💸",
                    titleUrdu = "رقم بھیج دی گئی! 💸",
                    messageEnglish = "Your withdrawal of PKR ${String.format("%.2f", target.amount)} has been approved and dispatched.",
                    messageUrdu = "آپ کے والٹ سے PKR ${String.format("%.2f", target.amount)} کی ادائیگی منظور اور روانہ کر دی گئی ہے۔",
                    type = "withdrawal_status"
                )
            )

            showToast("Withdrawal approved successfully!", "رقم کی واپسی منظور کر لی گئی!")
        }
    }

    fun adminRejectWithdrawal(withdrawalId: String, reason: String) {
        viewModelScope.launch {
            val target = withdrawals.value.find { it.id == withdrawalId } ?: return@launch
            val user = userFlow.value ?: return@launch

            // Revert payout: subtract pending hold, return back to availableBalance
            val updatedUser = user.copy(
                pendingBalance = (user.pendingBalance - target.amount).coerceAtLeast(0.0),
                availableBalance = user.availableBalance + target.amount
            )

            val updatedWithdrawal = target.copy(
                status = "rejected",
                adminNotes = reason,
                processedAt = System.currentTimeMillis()
            )

            repository.saveWithdrawalState(updatedWithdrawal, updatedUser)

            // Insert audit log
            val logId = "audit_${UUID.randomUUID().toString().take(6)}"
            db.appDao().insertAuditLog(
                AuditLogEntity(
                    id = logId,
                    action = "reject_withdrawal",
                    details = "Rejected withdrawal ID: $withdrawalId. Reason: $reason"
                )
            )

            // Dispatch notification
            val notifId = "notif_${UUID.randomUUID().toString().take(6)}"
            db.appDao().insertNotification(
                NotificationEntity(
                    id = notifId,
                    userId = "viewer_01",
                    titleEnglish = "Withdrawal Rejected ❌",
                    titleUrdu = "درخواست مسترد ❌",
                    messageEnglish = "Your withdrawal was rejected: $reason. Balance returned to your wallet.",
                    messageUrdu = "آپ کے رقم کی واپسی کی درخواست مسترد کر دی گئی۔ وجہ: $reason۔ رقم بٹوے میں واپس جمع ہو گئی ہے۔",
                    type = "withdrawal_status"
                )
            )

            showToast("Withdrawal request rejected. Balance refunded.", "درخواست مسترد، رقم بٹوے میں واپس جمع کر دی گئی۔")
        }
    }

    fun adminToggleUserBlocked(userId: String, isBlocked: Boolean) {
        viewModelScope.launch {
            repository.saveUserBlockStatus(userId, isBlocked)
            showToast(
                if (isBlocked) "User account suspended!" else "User account unsuspended!",
                if (isBlocked) "صارف کا اکاؤنٹ معطل کر دیا گیا!" else "صارف کا اکاؤنٹ بحال کر دیا گیا!"
            )
        }
    }

    fun adminUpdateRewardRule(ruleId: String, newValue: Double) {
        viewModelScope.launch {
            repository.updateRewardRule(ruleId, newValue)
            showToast("Earning rule updated successfully!", "کمائی کے اصول کو کامیابی سے اپ ڈیٹ کر دیا گیا!")
        }
    }

    fun adminSendAnnouncement(titleEn: String, titleUr: String, textEn: String, textUr: String) {
        viewModelScope.launch {
            repository.addAnnouncement(titleEn, titleUr, textEn, textUr)
            showToast("Broadcast announcement posted!", "سسٹم کا نیا اعلان نشر کر دیا گیا!")
        }
    }
}
