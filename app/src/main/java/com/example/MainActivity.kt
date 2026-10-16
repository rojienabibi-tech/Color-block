package com.example

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ads.UnityAdsManager
import com.example.audio.GameAudioService
import com.example.data.GameStorage
import com.example.model.GameMode
import com.example.model.PlayerStats
import com.example.model.ScreenType
import com.example.ui.components.GameLogo
import com.example.ui.screens.AboutScreen
import com.example.ui.screens.ClassicIntroScreen
import com.example.ui.screens.CollectionScreen
import com.example.ui.screens.DailyRewardsScreen
import com.example.ui.screens.DrawingBlockIntroScreen
import com.example.ui.screens.DrawingGameScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.JourneyMapScreen
import com.example.ui.screens.LevelGameScreen
import com.example.ui.screens.LoadingScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.ShopScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.TreasureBoxScreen
import com.example.ui.screens.TrophyRankingScreen
import com.example.ui.screens.TutorialScreen
import com.example.ui.screens.WelcomePopup
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private lateinit var audioService: GameAudioService
    private lateinit var gameStorage: GameStorage

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        audioService = GameAudioService()
        gameStorage = GameStorage(this)
        val settings = gameStorage.getSettings()
        audioService.soundEnabled = settings.soundEnabled
        audioService.musicEnabled = settings.musicEnabled

        UnityAdsManager.initialize(this)

        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ColorBlockApp(
                        audioService = audioService,
                        gameStorage = gameStorage,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        audioService.release()
    }
}

@Composable
fun ColorBlockApp(
    audioService: GameAudioService,
    gameStorage: GameStorage,
    modifier: Modifier = Modifier
) {
    var currentScreen by remember { mutableStateOf(ScreenType.SPLASH) }
    var gameMode by remember { mutableStateOf(GameMode.JOURNEY) }
    var classicBoardSize by remember { mutableIntStateOf(8) }
    var currentJourneyLevel by remember { mutableIntStateOf(1) }
    var showWelcomeModal by remember { mutableStateOf(false) }
    var isWelcomeTransitioning by remember { mutableStateOf(false) }
    var stats by remember { mutableStateOf(gameStorage.getStats()) }

    fun refreshStats() {
        stats = gameStorage.getStats()
    }

    val context = LocalContext.current
    val activity = context as? Activity

    fun showRewardedAd(
        rewardAmount: Int,
        successMessage: String,
        onSuccess: () -> Unit = {}
    ) {
        if (activity == null) return
        UnityAdsManager.showRewardedAd(
            activity = activity,
            rewardAmount = rewardAmount,
            onRewardEarned = { amount ->
                audioService.playDiamondChime()
                gameStorage.addDiamonds(amount)
                refreshStats()
                onSuccess()
                Toast.makeText(context, successMessage, Toast.LENGTH_SHORT).show()
            },
            onAdDismissed = {
                Toast.makeText(context, "Ad was skipped. No diamonds awarded.", Toast.LENGTH_SHORT).show()
            },
            onAdFailed = { error ->
                Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
            }
        )
    }

    LaunchedEffect(isWelcomeTransitioning) {
        if (isWelcomeTransitioning) {
            delay(4000)
            isWelcomeTransitioning = false
            currentScreen = ScreenType.LOADING
        }
    }

    // When Splash Screen finishes (after 5 seconds or Skip)
    val handleSplashFinish = {
        val currentStats = gameStorage.getStats()
        if (!currentStats.hasSeenWelcome) {
            showWelcomeModal = true
        } else {
            currentScreen = ScreenType.LOADING
        }
    }

    // When player taps LET'S PLAY on Welcome Popup
    val handleWelcomeLetsPlay = {
        val currentStats = gameStorage.getStats()
        gameStorage.saveStats(currentStats.copy(hasSeenWelcome = true))
        refreshStats()
        showWelcomeModal = false
        isWelcomeTransitioning = true
    }

    val handleTutorialComplete = {
        refreshStats()
        currentScreen = ScreenType.HOME
    }

    val handleTutorialGoHome = {
        refreshStats()
        currentScreen = ScreenType.HOME
    }

    val handleLoadingComplete = {
        currentScreen = ScreenType.HOME
    }

    val handleResetToFirstLaunch = {
        gameStorage.resetToFirstLaunch()
        refreshStats()
        showWelcomeModal = false
        isWelcomeTransitioning = false
        currentScreen = ScreenType.SPLASH
    }

    // Back handler for sub-screens
    BackHandler(enabled = currentScreen != ScreenType.HOME && currentScreen != ScreenType.SPLASH && currentScreen != ScreenType.LOADING && !isWelcomeTransitioning) {
        when (currentScreen) {
            ScreenType.GAME_LEVEL -> {
                currentScreen = if (gameMode == GameMode.CLASSIC) ScreenType.CLASSIC_INTRO else ScreenType.JOURNEY
            }
            ScreenType.DRAWING_GAME -> currentScreen = ScreenType.DRAWING_INTRO
            ScreenType.ABOUT -> currentScreen = ScreenType.SETTINGS
            ScreenType.TREASURE_BOX -> currentScreen = ScreenType.JOURNEY
            else -> currentScreen = ScreenType.HOME
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Main Screen Viewport
        when (currentScreen) {
            ScreenType.SPLASH -> {
                SplashScreen(onFinish = handleSplashFinish)
            }
            ScreenType.TUTORIAL -> {
                TutorialScreen(
                    onComplete = handleTutorialComplete,
                    onGoHome = handleTutorialGoHome,
                    audioService = audioService,
                    gameStorage = gameStorage
                )
            }
            ScreenType.LOADING -> {
                LoadingScreen(onComplete = handleLoadingComplete, targetSeconds = 21)
            }
            ScreenType.HOME -> {
                HomeScreen(
                    stats = stats,
                    onNavigate = { screen ->
                        refreshStats()
                        currentScreen = screen
                    }
                )
            }
            ScreenType.TROPHY -> {
                TrophyRankingScreen(
                    stats = stats,
                    onBack = { currentScreen = ScreenType.HOME }
                )
            }
            ScreenType.JOURNEY -> {
                JourneyMapScreen(
                    stats = stats,
                    onBack = { currentScreen = ScreenType.HOME },
                    onSelectLevel = { lvl ->
                        currentJourneyLevel = lvl
                        gameMode = GameMode.JOURNEY
                        currentScreen = ScreenType.GAME_LEVEL
                    },
                    onOpenTreasure = { currentScreen = ScreenType.TREASURE_BOX },
                    onWatchAd = {
                        showRewardedAd(
                            rewardAmount = 25,
                            successMessage = "Awesome! +25 Diamonds earned!"
                        )
                    }
                )
            }
            ScreenType.GAME_LEVEL -> {
                LevelGameScreen(
                    levelNumber = if (gameMode == GameMode.JOURNEY) currentJourneyLevel else 1,
                    targetScore = 1500,
                    mode = gameMode,
                    boardSize = if (gameMode == GameMode.CLASSIC) classicBoardSize else 8,
                    onBack = {
                        refreshStats()
                        currentScreen = if (gameMode == GameMode.CLASSIC) ScreenType.CLASSIC_INTRO else ScreenType.JOURNEY
                    },
                    onHome = {
                        refreshStats()
                        currentScreen = ScreenType.HOME
                    },
                    audioService = audioService,
                    gameStorage = gameStorage,
                    onWatchAd = { onRevive ->
                        showRewardedAd(
                            rewardAmount = 25,
                            successMessage = "Reward verified! Continuing game!",
                            onSuccess = onRevive
                        )
                    }
                )
            }
            ScreenType.TREASURE_BOX -> {
                TreasureBoxScreen(
                    onClaim = {
                        refreshStats()
                        currentScreen = ScreenType.JOURNEY
                    },
                    onBack = { currentScreen = ScreenType.JOURNEY },
                    audioService = audioService,
                    gameStorage = gameStorage
                )
            }
            ScreenType.SHOP -> {
                ShopScreen(
                    stats = stats,
                    onBack = {
                        refreshStats()
                        currentScreen = ScreenType.HOME
                    },
                    onRefreshStats = { refreshStats() },
                    audioService = audioService,
                    gameStorage = gameStorage,
                    onWatchAdForDiamonds = {
                        showRewardedAd(
                            rewardAmount = 50,
                            successMessage = "Awesome! +50 Free Diamonds earned!"
                        )
                    }
                )
            }
            ScreenType.COLLECTION -> {
                CollectionScreen(onBack = { currentScreen = ScreenType.HOME })
            }
            ScreenType.DAILY_REWARDS -> {
                DailyRewardsScreen(
                    stats = stats,
                    onBack = {
                        refreshStats()
                        currentScreen = ScreenType.HOME
                    },
                    onRefreshStats = { refreshStats() },
                    audioService = audioService,
                    gameStorage = gameStorage
                )
            }
            ScreenType.SETTINGS -> {
                SettingsScreen(
                    onBack = { currentScreen = ScreenType.HOME },
                    onNavigateAbout = { currentScreen = ScreenType.ABOUT },
                    onResetFirstLaunch = handleResetToFirstLaunch,
                    audioService = audioService,
                    gameStorage = gameStorage
                )
            }
            ScreenType.ABOUT -> {
                AboutScreen(onBack = { currentScreen = ScreenType.SETTINGS })
            }
            ScreenType.CLASSIC_INTRO -> {
                ClassicIntroScreen(
                    bestScore = stats.bestClassicScore,
                    onPlay = { chosenSize ->
                        gameMode = GameMode.CLASSIC
                        classicBoardSize = chosenSize
                        currentScreen = ScreenType.GAME_LEVEL
                    },
                    onBack = { currentScreen = ScreenType.HOME }
                )
            }
            ScreenType.DRAWING_INTRO -> {
                DrawingBlockIntroScreen(
                    onPlay = { currentScreen = ScreenType.DRAWING_GAME },
                    onBack = { currentScreen = ScreenType.HOME }
                )
            }
            ScreenType.DRAWING_GAME -> {
                DrawingGameScreen(
                    onBack = { currentScreen = ScreenType.DRAWING_INTRO },
                    onHome = {
                        refreshStats()
                        currentScreen = ScreenType.HOME
                    },
                    audioService = audioService,
                    gameStorage = gameStorage
                )
            }
            else -> {
                HomeScreen(stats = stats, onNavigate = { currentScreen = it })
            }
        }

        // First-launch Welcome Modal
        if (showWelcomeModal) {
            WelcomePopup(onPlayClick = handleWelcomeLetsPlay)
        }

        // 4-second transition screen between Welcome Popup and Loading Screen
        if (isWelcomeTransitioning) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF5335D2), Color(0xFF3575DF), Color(0xFF2994F2))
                        )
                    )
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    GameLogo(compact = false, showStar = true)
                    Spacer(modifier = Modifier.height(28.dp))
                    Text(
                        text = "Preparing your colorful world...",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Opening Loading Screen in a moment",
                        fontSize = 13.sp,
                        color = Color(0xFFBAE6FD)
                    )
                }
            }
        }

    }
}
