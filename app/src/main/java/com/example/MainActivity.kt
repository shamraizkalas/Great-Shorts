package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.db.*
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme(darkTheme = false) { // Clean Minimalism Light Theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(viewModel)
                }
            }
        }
    }
}

@Composable
fun MainScreen(viewModel: AppViewModel) {
    val activeTab by viewModel.activeTab.collectAsState()
    val language by viewModel.language.collectAsState()
    val user by viewModel.userFlow.collectAsState()

    val toastEn by viewModel.toastMessageEn.collectAsState()
    val toastUr by viewModel.toastMessageUr.collectAsState()

    // Toast message handler
    LaunchedEffect(toastEn, toastUr) {
        if (toastEn != null) {
            delay(3500)
            viewModel.clearToast()
        }
    }

    Scaffold(
        bottomBar = {
            BottomNavigationBar(
                activeTab = activeTab,
                onTabSelected = { viewModel.setTab(it) },
                lang = language
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
        ) {
            // Main Content router
            when (activeTab) {
                AppTab.FEED -> VideoFeedScreen(viewModel, language)
                AppTab.WALLET -> WalletDashboardScreen(viewModel, language)
                AppTab.REFERRALS -> ReferralsScreen(viewModel, language)
                AppTab.INBOX -> InboxScreen(viewModel, language)
                AppTab.PROFILE -> ProfileScreen(viewModel, language)
                AppTab.ADMIN -> AdminPortalScreen(viewModel, language)
            }

            // High-fidelity bilingual persistent toast alert overlay
            toastEn?.let { msgEn ->
                val msgUr = toastUr ?: ""
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(16.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF1E3C72), Color(0xFF2A5298))
                            )
                        )
                        .border(1.dp, Color(0xFF64B5F6), RoundedCornerShape(16.dp))
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                        .animateContentSize()
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = msgEn,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        if (msgUr.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = msgUr,
                                color = Color(0xFFE3F2FD),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Normal,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BottomNavigationBar(activeTab: AppTab, onTabSelected: (AppTab) -> Unit, lang: Language) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 4.dp,
        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        val items = listOf(
            Triple(AppTab.FEED, Icons.Default.PlayArrow, "Feed" to "فلیش"),
            Triple(AppTab.WALLET, Icons.Default.AccountBalanceWallet, "Wallet" to "پرس"),
            Triple(AppTab.REFERRALS, Icons.Default.People, "Invite" to "دعوت"),
            Triple(AppTab.INBOX, Icons.Default.Notifications, "Inbox" to "ان باکس"),
            Triple(AppTab.PROFILE, Icons.Default.Person, "Me" to "میں"),
            Triple(AppTab.ADMIN, Icons.Default.AdminPanelSettings, "Admin" to "ایڈمن")
        )

        items.forEach { (tab, icon, labels) ->
            val isSelected = activeTab == tab
            NavigationBarItem(
                selected = isSelected,
                onClick = { onTabSelected(tab) },
                icon = {
                    Icon(
                        imageVector = icon,
                        contentDescription = labels.first,
                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(24.dp)
                    )
                },
                label = {
                    Text(
                        text = if (lang == Language.ENGLISH) labels.first else labels.second,
                        fontSize = 10.sp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
            )
        }
    }
}

// ==========================================
// 1. VIDEO FEED SCREEN (GREATSHORT PLAYER)
// ==========================================
@Composable
fun VideoFeedScreen(viewModel: AppViewModel, lang: Language) {
    val videoList by viewModel.videos.collectAsState()
    val currentIndex by viewModel.currentPlayingVideoIndex.collectAsState()
    val user by viewModel.userFlow.collectAsState()

    // Fraud/Risk Toggles for verification testing
    var simulateAbnormalSpeed by remember { mutableStateOf(false) }
    var simulateEmulator by remember { mutableStateOf(false) }

    if (videoList.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color(0xFF00E676))
        }
        return
    }

    val activeVideo = videoList[currentIndex]

    // Watch Progress loop simulation
    var watchSecondsElapsed by remember(activeVideo.id) { mutableStateOf(0) }
    var isPlaying by remember { mutableStateOf(true) }

    // Earning credit circle progress
    val creditGoalSeconds = 5
    var creditProgress by remember(activeVideo.id) { mutableStateOf(0f) }

    LaunchedEffect(isPlaying, currentIndex) {
        if (isPlaying) {
            while (true) {
                delay(1000)
                watchSecondsElapsed += 1
                val ratio = (watchSecondsElapsed % creditGoalSeconds).toFloat() / creditGoalSeconds
                creditProgress = if (ratio == 0f) 1f else ratio

                // When progress fills, submit secure watch event to repo
                if (watchSecondsElapsed % creditGoalSeconds == 0) {
                    viewModel.submitVideoWatchTime(
                        videoId = activeVideo.id,
                        seconds = creditGoalSeconds,
                        simulateAbnormalSpeed = simulateAbnormalSpeed,
                        simulateEmulator = simulateEmulator
                    )
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Immersive video backdrop container
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF1A1A24), Color(0xFF000000))
                    )
                )
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Video Player Simulator Graphic
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    // Aesthetic graphic layout
                    Image(
                        painter = painterResource(id = android.R.drawable.ic_menu_slideshow),
                        contentDescription = "Playing",
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(0.12f),
                        contentScale = ContentScale.Crop,
                        colorFilter = ColorFilter.tint(Color(0xFF00E676))
                    )

                    // Display spinning record or play icon indicator
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.MovieFilter else Icons.Default.PlayArrow,
                            contentDescription = "Play icon",
                            tint = Color(0xFF00E676),
                            modifier = Modifier
                                .size(82.dp)
                                .clickable { isPlaying = !isPlaying }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (isPlaying) "SIMULATING VIDEO PLAYBACK..." else "PAUSED",
                            color = Color.LightGray,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "URL: ${activeVideo.videoUrl.take(45)}...",
                            color = Color.DarkGray,
                            fontSize = 9.sp
                        )
                    }

                    // Bottom fade overlay
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color(0xAA000000), Color.Black)
                                )
                            )
                    )

                    // Video content titles (Urdu/English Bilingual text)
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp)
                            .padding(bottom = 24.dp)
                            .fillMaxWidth(0.75f)
                    ) {
                        Text(
                            text = activeVideo.titleEnglish,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = activeVideo.titleUrdu,
                            color = Color(0xFF00E676),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Normal
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (lang == Language.ENGLISH) activeVideo.descriptionEnglish else activeVideo.descriptionUrdu,
                            color = Color.LightGray,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Visibility, contentDescription = "Views", tint = Color.Gray, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("${activeVideo.viewsCount} views", color = Color.Gray, fontSize = 11.sp)
                        }
                    }

                    // Video player action bar (Right align)
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp)
                            .padding(bottom = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 1. Earn credit ring counter
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color(0x99000000))
                                .border(2.dp, Color(0xFF2A2A35), CircleShape)
                        ) {
                            CircularProgressIndicator(
                                progress = creditProgress,
                                strokeWidth = 4.dp,
                                color = Color(0xFF00E676),
                                modifier = Modifier.size(54.dp)
                            )
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "PKR",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Yellow
                                )
                                Text(
                                    text = "+0.50",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White
                                )
                            }
                        }

                        // 2. Bookmark Action
                        val isBookmarked = viewModel.bookmarkedVideos.collectAsState().value.any { it.videoId == activeVideo.id }
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color(0x99000000))
                                .clickable { viewModel.toggleBookmark(activeVideo.id) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = "Bookmark",
                                tint = if (isBookmarked) Color.Yellow else Color.White
                            )
                        }

                        // 3. Up/Down Episode keys
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color(0x99000000))
                                .clickable { viewModel.selectPreviousVideo() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.ArrowUpward, contentDescription = "Prev Video", tint = Color.White)
                        }

                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color(0x99000000))
                                .clickable { viewModel.selectNextVideo() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.ArrowDownward, contentDescription = "Next Video", tint = Color.White)
                        }
                    }
                }

                // Top Controls: Urdu vs English toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "G",
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        Text(
                            text = "GreatShort Pro",
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.toggleLanguage() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(
                                text = if (lang == Language.ENGLISH) "اردو" else "ENGLISH",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // SECURITY & RISK TESTING TRAY (Allows testing anti-fraud holds)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                        .padding(8.dp)
                ) {
                    Text(
                        text = "🛡️ SECURE VERIFICATION TESTING CONTROLS",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = simulateAbnormalSpeed,
                                onCheckedChange = { simulateAbnormalSpeed = it },
                                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                            )
                            Text(
                                text = if (lang == Language.ENGLISH) "Spoof Watch Speed" else "تیزی کی نقل کریں",
                                color = if (simulateAbnormalSpeed) MaterialTheme.colorScheme.primary else Color.Gray,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = simulateEmulator,
                                onCheckedChange = { simulateEmulator = it },
                                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                            )
                            Text(
                                text = if (lang == Language.ENGLISH) "Spoof Emulator" else "امیولیٹر کی نقل کریں",
                                color = if (simulateEmulator) MaterialTheme.colorScheme.primary else Color.Gray,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 2. WALLET DASHBOARD & CASH-OUT SCREEN
// ==========================================
@Composable
fun WalletDashboardScreen(viewModel: AppViewModel, lang: Language) {
    val user by viewModel.userFlow.collectAsState()
    val watchEvents by viewModel.watchEvents.collectAsState()
    val withdrawals by viewModel.withdrawals.collectAsState()

    var withdrawAmount by remember { mutableStateOf("") }
    var accountMethod by remember { mutableStateOf("easypaisa") }
    var accountNum by remember { mutableStateOf("") }
    var accountTitle by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Bilingual Title header
        item {
            Column {
                Text(
                    text = if (lang == Language.ENGLISH) "Earnings Wallet" else "کمائی والٹ",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = if (lang == Language.ENGLISH) "Track earnings & request cashouts safely" else "اپنی کمائی کی نگرانی کریں اور رقم واپس حاصل کریں",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
        }

        // Available Balance Card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(24.dp)
            ) {
                Column {
                    Text(
                        text = if (lang == Language.ENGLISH) "Available Balance" else "دستیاب رقم",
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "PKR ${String.format("%.2f", user?.availableBalance ?: 0.00)}",
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = if (lang == Language.ENGLISH) "Pending" else "زیر التواء",
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                                fontSize = 11.sp
                            )
                            Text(
                                text = "PKR ${String.format("%.2f", user?.pendingBalance ?: 0.00)}",
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Column {
                            Text(
                                text = if (lang == Language.ENGLISH) "Withdrawn" else "نکالی گئی رقم",
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                                fontSize = 11.sp
                            )
                            Text(
                                text = "PKR ${String.format("%.2f", user?.totalWithdrawn ?: 0.00)}",
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Column {
                            Text(
                                text = if (lang == Language.ENGLISH) "Lifetime" else "کل کمائی",
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                                fontSize = 11.sp
                            )
                            Text(
                                text = "PKR ${String.format("%.2f", user?.lifetimeEarned ?: 0.00)}",
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // CASH-OUT FORM PANEL
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (lang == Language.ENGLISH) "Request Cash-Out" else "رقم نکلوائیں",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Amount Input
                    OutlinedTextField(
                        value = withdrawAmount,
                        onValueChange = { withdrawAmount = it },
                        label = { Text("Amount (PKR) / رقم") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("withdraw_amount_input")
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Account Method Selectors
                    Text(
                        text = if (lang == Language.ENGLISH) "Select Account Type" else "اکاؤنٹ کی قسم منتخب کریں",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val methods = listOf("easypaisa" to "EasyPaisa", "jazzcash" to "JazzCash", "bank" to "Bank Transfer")
                        methods.forEach { (slug, name) ->
                            val active = accountMethod == slug
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                                    .border(1.dp, if (active) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(8.dp))
                                    .clickable { accountMethod = slug }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = name,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    // Account Number
                    OutlinedTextField(
                        value = accountNum,
                        onValueChange = { accountNum = it },
                        label = { Text("Account Number / موبائل نمبر") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Account Title
                    OutlinedTextField(
                        value = accountTitle,
                        onValueChange = { accountTitle = it },
                        label = { Text("Account Title / نام") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            val amt = withdrawAmount.toDoubleOrNull()
                            if (amt == null || amt <= 0) {
                                viewModel.showToast("Please enter a valid amount", "درست رقم درج کریں")
                            } else if (accountNum.isBlank() || accountTitle.isBlank()) {
                                viewModel.showToast("Please complete all fields", "تمام خانوں کو پُر کریں")
                            } else {
                                viewModel.requestWithdraw(amt, accountMethod, accountNum, accountTitle)
                                withdrawAmount = ""
                                accountNum = ""
                                accountTitle = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text(
                            text = if (lang == Language.ENGLISH) "SUBMIT CASH-OUT REQUEST" else "درخواست جمع کروائیں",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Payout Transactions History List
        item {
            Text(
                text = if (lang == Language.ENGLISH) "Recent Payout Statuses" else "نکالی گئی رقوم کی تفصیلات",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        if (withdrawals.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No withdrawal events recorded yet.", color = Color.Gray, fontSize = 12.sp)
                }
            }
        } else {
            items(withdrawals) { w ->
                WithdrawalItemRow(w, lang)
            }
        }
    }
}

@Composable
fun WithdrawalItemRow(w: WithdrawalEntity, lang: Language) {
    val statusColor = when (w.status) {
        "approved" -> Color(0xFF2E7D32) // Soft forest green in light mode
        "rejected" -> Color(0xFFC62828) // Deep compliance red
        else -> Color(0xFFEF6C00) // Deep warm amber
    }

    val statusText = when (w.status) {
        "approved" -> if (lang == Language.ENGLISH) "APPROVED & PAID" else "منظور شدہ اور ادا شدہ"
        "rejected" -> if (lang == Language.ENGLISH) "REJECTED" else "مسترد شدہ"
        else -> if (lang == Language.ENGLISH) "UNDER AUDIT REVIEW" else "جانچ پڑتال جاری"
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${w.method.uppercase()} Payout",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "A/C: ${w.accountNumber} (${w.accountTitle})",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
                w.adminNotes?.let { notes ->
                    Text("Reason: $notes", color = Color(0xFFC62828), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "PKR ${String.format("%.2f", w.amount)}",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(statusColor.copy(alpha = 0.12f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = statusText,
                        color = statusColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ==========================================
// 3. REFERRALS SCREEN
// ==========================================
@Composable
fun ReferralsScreen(viewModel: AppViewModel, lang: Language) {
    val user by viewModel.userFlow.collectAsState()
    var inviteCodeInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = if (lang == Language.ENGLISH) "Invite & Earn" else "دوستوں کو مدعو کریں",
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground
        )

        // Referral Card Displaying your code
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (lang == Language.ENGLISH) "YOUR EXCLUSIVE REFERRAL CODE" else "آپ کا مخصوص دعوت نامہ کوڈ",
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = user?.referralCode ?: "SHAM7788",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = if (lang == Language.ENGLISH) "Earn PKR 100 for every friend who joins and watches their first drama." else "ہر اس دوست پر PKR 100 حاصل کریں جو آپ کے کوڈ سے جوائن کرتا ہے اور پہلی ویڈیو دیکھتا ہے۔",
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp
                )
            }
        }

        // Apply Referee Invitation Code Form
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = if (lang == Language.ENGLISH) "Apply Invitation Code" else "دعوت نامہ کوڈ درج کریں",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (lang == Language.ENGLISH) "Were you invited? Apply their code to get PKR 50 instantly!" else "کیا آپ کو کسی دوست نے مدعو کیا تھا؟ فوری طور پر PKR 50 حاصل کریں!",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                if (user?.referredBy != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "Code applied: ${user?.referredBy} (PKR 50.00 claimed)",
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = inviteCodeInput,
                            onValueChange = { inviteCodeInput = it },
                            placeholder = { Text("Friend's Code") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        Button(
                            onClick = {
                                viewModel.applyReferral(inviteCodeInput)
                                inviteCodeInput = ""
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.height(56.dp)
                        ) {
                            Text("APPLY", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 4. INBOX / ANNOUNCEMENT NOTIFICATIONS
// ==========================================
@Composable
fun InboxScreen(viewModel: AppViewModel, lang: Language) {
    val list by viewModel.notifications.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column {
                Text(
                    text = if (lang == Language.ENGLISH) "Notification Inbox" else "ان باکس",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = if (lang == Language.ENGLISH) "Important reward updates & administrative news" else "انعامات اور سسٹم کے اہم اعلانات",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
        }

        if (list.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No inbox updates recorded.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            }
        } else {
            items(list) { n ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (lang == Language.ENGLISH) n.titleEnglish else n.titleUrdu,
                                color = if (n.type == "announcement") Color(0xFFEF6C00) else Color(0xFF2E7D32),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                imageVector = when (n.type) {
                                    "announcement" -> Icons.Default.Campaign
                                    "withdrawal_status" -> Icons.Default.Payment
                                    else -> Icons.Default.Celebration
                                },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (lang == Language.ENGLISH) n.messageEnglish else n.messageUrdu,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// 5. PROFILE & RETENTION CONTROLS
// ==========================================
@Composable
fun ProfileScreen(viewModel: AppViewModel, lang: Language) {
    val user by viewModel.userFlow.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = if (lang == Language.ENGLISH) "Profile & Settings" else "پروفائل اور ترتیبات",
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground
        )

        // Viewer Detail Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Large generic M3 avatar symbol
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(32.dp))
                }
                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Text(
                        text = user?.displayName ?: "Shamraiz Kalas",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Level ${user?.accountLevel ?: 2} Viewer / صارف لیول",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Watched Minutes: ${user?.watchedMinutes ?: 42} mins",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }
        }

        // DAILY REWARD CHECK-IN PANEL
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (lang == Language.ENGLISH) "Daily Attendance Streak" else "روزانہ حاضری اسٹریک",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${user?.streakDays ?: 5} Days",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (lang == Language.ENGLISH) "Log your check-in daily. Achieve continuous 7-day milestones for a giant PKR 50.00 bonus!" else "روزانہ اپنی حاضری درج کریں اور مسلسل 7 دن مکمل کرنے پر PKR 50.00 کا بڑا انعام حاصل کریں!",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { viewModel.performDailyCheckIn() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text(
                        text = if (lang == Language.ENGLISH) "REGISTER DAILY ATTENDANCE" else "حاضری درج کریں",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // REGULATORY COMPLIANCE ACTIONS
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Compliance & GDPR Tools",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        viewModel.showToast(
                            "Account Deletion requested. Data scrubbed in 30 days.",
                            "اکاؤنٹ ختم کرنے کی درخواست موصول۔ 30 دن میں ڈیٹا حذف کر دیا جائے گا۔"
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Request Account Deletion / اکاؤنٹ حذف کریں", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        viewModel.showToast(
                            "Policy Logged: Consents captured.",
                            "سیکیورٹی لاگ: رازداری کی رضامندی رجسٹرڈ۔"
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Review Terms & Privacy / رازداری کی پالیسی", fontSize = 11.sp)
                }
            }
        }
    }
}

// ==========================================
// 6. ADMINISTRATIVE PORTAL SCREEN (SANDBOX)
// ==========================================
@Composable
fun AdminPortalScreen(viewModel: AppViewModel, lang: Language) {
    val withdrawals by viewModel.withdrawals.collectAsState()
    val rules by viewModel.rewardRules.collectAsState()
    val fraudSignals by viewModel.fraudSignals.collectAsState()
    val user by viewModel.userFlow.collectAsState()

    var announceTitleEn by remember { mutableStateOf("") }
    var announceTitleUr by remember { mutableStateOf("") }
    var announceTextEn by remember { mutableStateOf("") }
    var announceTextUr by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text(
                    text = "Admin Simulation Sandbox",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Review fraud, approve withdrawals, and edit rules in real-time.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
        }

        // 1. MANAGE REWARD RULES CONTROLS
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "🔧 Configure Reward Multipliers (Live Rules)",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    rules.forEach { rule ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(rule.name, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        viewModel.adminUpdateRewardRule(rule.id, rule.value - 0.05)
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    contentPadding = PaddingValues(0.dp),
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Text("-", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                                Text(
                                    text = String.format("%.2f", rule.value),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Button(
                                    onClick = {
                                        viewModel.adminUpdateRewardRule(rule.id, rule.value + 0.05)
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    contentPadding = PaddingValues(0.dp),
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Text("+", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. MANAGE BLOCK/UNBLOCK USER ACTIONS
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "🚫 Block / Suspend User Profile",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Force locks wallet, and prevents reward submissions.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("User ID: viewer_01", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = if (user?.isBlocked == true) "Blocked / suspended" else "Active / Active",
                                color = if (user?.isBlocked == true) Color(0xFFC62828) else Color(0xFF2E7D32),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = {
                                viewModel.adminToggleUserBlocked("viewer_01", user?.isBlocked == false)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (user?.isBlocked == true) Color(0xFF2E7D32) else Color(0xFFC62828),
                                contentColor = Color.White
                            )
                        ) {
                            Text(
                                text = if (user?.isBlocked == true) "UNBLOCK" else "BLOCK USER",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // 3. BROADCAST ANNOUNCEMENT SENDER
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "📢 Post Public Announcement",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = announceTitleEn,
                        onValueChange = { announceTitleEn = it },
                        label = { Text("Title (English)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = announceTitleUr,
                        onValueChange = { announceTitleUr = it },
                        label = { Text("Title (Urdu)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = announceTextEn,
                        onValueChange = { announceTextEn = it },
                        label = { Text("Content (English)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = announceTextUr,
                        onValueChange = { announceTextUr = it },
                        label = { Text("Content (Urdu)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            if (announceTitleEn.isNotBlank() && announceTextEn.isNotBlank()) {
                                viewModel.adminSendAnnouncement(announceTitleEn, announceTitleUr, announceTextEn, announceTextUr)
                                announceTitleEn = ""
                                announceTitleUr = ""
                                announceTextEn = ""
                                announceTextUr = ""
                            } else {
                                viewModel.showToast("Announcements require titles and bodies", "مکمل کریں")
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("BROADCAST TO ALL INBOXES", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }

        // 4. SUSPICIOUS FRAUD ALERTS LIST
        item {
            Text(
                text = "🛡️ Suspicious Fraud Signal Logs",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        val pendingSignals = fraudSignals.filter { !it.isResolved }
        if (pendingSignals.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No fraud signals detected yet. Green light.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            }
        } else {
            items(pendingSignals) { sig ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = sig.signalType.uppercase().replace("_", " "),
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${sig.severity.uppercase()} severity",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 11.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(sig.details, fontSize = 11.sp)
                    }
                }
            }
        }

        // 5. MANUAL APPROVAL OF WITHDRAWAL PAYOUT REQUESTS
        item {
            Text(
                text = "💸 Pending Withdrawal Auditing Queue",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        val pendingPayouts = withdrawals.filter { it.status == "pending" }
        if (pendingPayouts.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Audit queue empty. No pending withdrawals.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            }
        } else {
            items(pendingPayouts) { p ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("${p.method.uppercase()} Payout request", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text("A/C: ${p.accountNumber} (${p.accountTitle})", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("PKR ${p.amount}", color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Text("Fraud Risk: ${p.riskScore}%", color = if (p.riskScore >= 40) Color(0xFFC62828) else Color(0xFF2E7D32), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { viewModel.adminRejectWithdrawal(p.id, "Failed risk audit") },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("REJECT", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }

                            Button(
                                onClick = { viewModel.adminApproveWithdrawal(p.id) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("APPROVE & DISPATCH", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
