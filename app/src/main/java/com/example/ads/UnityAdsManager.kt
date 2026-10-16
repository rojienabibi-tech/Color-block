package com.example.ads

import android.app.Activity
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import com.example.BuildConfig
import com.unity3d.ads.IUnityAdsInitializationListener
import com.unity3d.ads.IUnityAdsLoadListener
import com.unity3d.ads.IUnityAdsShowListener
import com.unity3d.ads.UnityAds
import com.unity3d.ads.UnityAdsLoadOptions
import com.unity3d.ads.UnityAdsShowOptions
import com.unity3d.ads.metadata.MetaData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Collections
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Reusable Unity Ads Service for COLOR BLOCK.
 * Configured specifically for:
 * - Android Game ID: 800399465
 * - Rewarded Placement ID: BP_Rewarded_Android
 * - Optional Rewarded Ads only (no banner, no interstitial, no app-open)
 * - Production Live Ads with Test Mode disabled
 * - Strict single-reward validation and duplicate prevention
 */
object UnityAdsManager {
    private const val TAG = "ColorBlockUnityAds"
    const val GAME_ID: String = BuildConfig.UNITY_ADS_GAME_ID
    const val REWARDED_PLACEMENT_ID: String = BuildConfig.UNITY_ADS_PLACEMENT_ID

    // Production Live Ads setting: Test Mode is false for real ad servers
    var forceProductionLiveAds: Boolean = true
    val testMode: Boolean
        get() = BuildConfig.UNITY_ADS_TEST_MODE && !forceProductionLiveAds

    private var currentAdObjectId: String? = null

    enum class SdkStatus {
        NOT_INITIALIZED,
        INITIALIZING,
        INITIALIZED,
        FAILED
    }

    enum class AdLoadStatus {
        IDLE,
        LOADING,
        READY,
        FAILED
    }

    private val _sdkStatus = MutableStateFlow(SdkStatus.NOT_INITIALIZED)
    val sdkStatus: StateFlow<SdkStatus> = _sdkStatus.asStateFlow()

    private val _adLoadStatus = MutableStateFlow(AdLoadStatus.IDLE)
    val adLoadStatus: StateFlow<AdLoadStatus> = _adLoadStatus.asStateFlow()

    private val _isAdReady = MutableStateFlow(false)
    val isAdReady: StateFlow<Boolean> = _isAdReady.asStateFlow()

    private val _lastStatusMessage = MutableStateFlow("Ready to initialize")
    val lastStatusMessage: StateFlow<String> = _lastStatusMessage.asStateFlow()

    private val isShowingAd = AtomicBoolean(false)
    private var activeSessionId: String? = null
    private val rewardedSessions = Collections.synchronizedSet(mutableSetOf<String>())

    // Tracks total rewards verified and delivered during runtime
    private val _totalDeliveredRewards = MutableStateFlow(0)
    val totalDeliveredRewards: StateFlow<Int> = _totalDeliveredRewards.asStateFlow()

    /**
     * Check if network connectivity is active.
     */
    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities =
            connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /**
     * Configure GDPR and CCPA privacy and consent compliance metadata.
     */
    fun configurePrivacyConsent(context: Context, userConsented: Boolean = true) {
        try {
            val gdprMetaData = MetaData(context.applicationContext)
            gdprMetaData.set("gdpr.consent", userConsented)
            gdprMetaData.commit()

            val ccpaMetaData = MetaData(context.applicationContext)
            ccpaMetaData.set("privacy.consent", userConsented)
            ccpaMetaData.set("privacy.mode", "mixed")
            ccpaMetaData.commit()
            Log.i(TAG, "Privacy compliance configured: GDPR & CCPA consent=$userConsented")
        } catch (e: Exception) {
            Log.w(TAG, "Notice setting privacy metadata: ${e.message}")
        }
    }

    /**
     * Initialize the official Unity Ads SDK early in the app lifecycle.
     * Connects to live advertising service when testMode is false.
     * Prevents duplicate/repeated initialization.
     */
    @Synchronized
    fun initialize(context: Context, onComplete: ((Boolean) -> Unit)? = null) {
        if (_sdkStatus.value == SdkStatus.INITIALIZED) {
            Log.d(TAG, "Unity Ads SDK already initialized with Game ID: $GAME_ID (Live Ad Mode=${!testMode})")
            onComplete?.invoke(true)
            return
        }
        if (_sdkStatus.value == SdkStatus.INITIALIZING) {
            Log.d(TAG, "Unity Ads SDK is already in the process of initializing...")
            return
        }

        configurePrivacyConsent(context, userConsented = true)

        _sdkStatus.value = SdkStatus.INITIALIZING
        _lastStatusMessage.value = "Connecting to Unity Ads live servers..."
        val currentTestMode = testMode
        Log.i(TAG, "Initializing Unity Ads SDK - Game ID: $GAME_ID, Test Mode: $currentTestMode (Live Production Ads=${!currentTestMode})")

        try {
            UnityAds.initialize(
                context.applicationContext,
                GAME_ID,
                currentTestMode,
                object : IUnityAdsInitializationListener {
                    override fun onInitializationComplete() {
                        _sdkStatus.value = SdkStatus.INITIALIZED
                        _lastStatusMessage.value = if (!currentTestMode) "Unity Ads Live Service connected" else "Unity Ads initialized"
                        Log.i(TAG, "Unity Ads initialization complete. SDK connected and ready.")
                        onComplete?.invoke(true)
                    }

                    override fun onInitializationFailed(
                        error: UnityAds.UnityAdsInitializationError,
                        message: String
                    ) {
                        _sdkStatus.value = SdkStatus.FAILED
                        _lastStatusMessage.value = "Initialization failed: $message"
                        Log.e(TAG, "Unity Ads initialization failed: $error - $message")
                        onComplete?.invoke(false)
                    }
                }
            )
        } catch (e: Exception) {
            _sdkStatus.value = SdkStatus.FAILED
            _lastStatusMessage.value = "Init exception: ${e.message}"
            Log.e(TAG, "Exception during Unity Ads initialization", e)
            onComplete?.invoke(false)
        }
    }

    /**
     * Load the Rewarded Ad placement BP_Rewarded_Android.
     */
    fun loadRewardedAd(context: Context, onResult: ((Boolean, String?) -> Unit)? = null) {
        if (_sdkStatus.value != SdkStatus.INITIALIZED) {
            val msg = "Cannot load ad: SDK not initialized yet."
            _lastStatusMessage.value = msg
            Log.w(TAG, msg)
            onResult?.invoke(false, msg)
            return
        }

        if (_adLoadStatus.value == AdLoadStatus.LOADING) {
            Log.d(TAG, "Ad load request already in progress for placement: $REWARDED_PLACEMENT_ID")
            return
        }

        _adLoadStatus.value = AdLoadStatus.LOADING
        _lastStatusMessage.value = "Loading rewarded ad..."
        Log.d(TAG, "Loading rewarded ad for placement: $REWARDED_PLACEMENT_ID")

        val generatedObjectId = UUID.randomUUID().toString()
        currentAdObjectId = generatedObjectId

        val loadOptions = UnityAdsLoadOptions().apply {
            objectId = generatedObjectId
        }

        try {
            UnityAds.load(
                REWARDED_PLACEMENT_ID,
                loadOptions,
                object : IUnityAdsLoadListener {
                    override fun onUnityAdsAdLoaded(placementId: String) {
                        if (placementId == REWARDED_PLACEMENT_ID) {
                            _adLoadStatus.value = AdLoadStatus.READY
                            _isAdReady.value = true
                            _lastStatusMessage.value = "Rewarded ad ready ($placementId)"
                            Log.i(TAG, "Rewarded ad loaded successfully for placement: $placementId with objectId: $generatedObjectId")
                            onResult?.invoke(true, null)
                        }
                    }

                    override fun onUnityAdsFailedToLoad(
                        placementId: String,
                        error: UnityAds.UnityAdsLoadError,
                        message: String
                    ) {
                        _adLoadStatus.value = AdLoadStatus.FAILED
                        _isAdReady.value = false
                        _lastStatusMessage.value = "Failed to load ad: $message"
                        Log.w(TAG, "Unity Ads load callback for $placementId: $error - $message")
                        onResult?.invoke(false, message)
                    }
                }
            )
        } catch (e: Exception) {
            _adLoadStatus.value = AdLoadStatus.FAILED
            _isAdReady.value = false
            _lastStatusMessage.value = "Load exception: ${e.message}"
            Log.e(TAG, "Exception loading rewarded ad", e)
            onResult?.invoke(false, e.message)
        }
    }

    /**
     * Show the Rewarded Ad to the user.
     *
     * Strict rules:
     * - Debounced: Prevents repeated button taps while ad is launching or playing.
     * - Verified: Grants reward ONLY if state is COMPLETED.
     * - Deduplicated: Session ID prevents double-rewarding from accidental multiple callbacks.
     */
    fun showRewardedAd(
        activity: Activity,
        rewardAmount: Int = 50,
        onRewardEarned: (rewardAmount: Int) -> Unit,
        onAdDismissed: (() -> Unit)? = null,
        onAdFailed: (String) -> Unit
    ) {
        // Debounce: prevent duplicate button taps
        if (!isShowingAd.compareAndSet(false, true)) {
            Log.w(TAG, "Ad display requested while another ad is active or opening. Ignoring tap.")
            return
        }

        // Check network
        if (!isNetworkAvailable(activity)) {
            isShowingAd.set(false)
            val msg = "No internet connection. Please check your network to watch ads."
            _lastStatusMessage.value = msg
            onAdFailed(msg)
            return
        }

        // Check ad readiness
        if (!_isAdReady.value) {
            isShowingAd.set(false)
            val msg = "Rewarded ad is currently loading or unavailable. Please try again in a moment."
            _lastStatusMessage.value = msg
            loadRewardedAd(activity.applicationContext)
            onAdFailed(msg)
            return
        }

        val sessionId = UUID.randomUUID().toString()
        activeSessionId = sessionId
        _lastStatusMessage.value = "Showing rewarded ad..."
        Log.i(TAG, "Showing rewarded ad for placement: $REWARDED_PLACEMENT_ID (Session: $sessionId)")

        val showOptions = UnityAdsShowOptions().apply {
            currentAdObjectId?.let { objectId = it }
        }

        try {
            UnityAds.show(
                activity,
                REWARDED_PLACEMENT_ID,
                showOptions,
                object : IUnityAdsShowListener {
                    override fun onUnityAdsShowStart(placementId: String) {
                        Log.d(TAG, "Rewarded ad show started for placement: $placementId")
                    }

                    override fun onUnityAdsShowClick(placementId: String) {
                        Log.d(TAG, "Rewarded ad clicked by user for placement: $placementId")
                    }

                    override fun onUnityAdsShowComplete(
                        placementId: String,
                        state: UnityAds.UnityAdsShowCompletionState
                    ) {
                        isShowingAd.set(false)
                        _isAdReady.value = false
                        _adLoadStatus.value = AdLoadStatus.IDLE
                        Log.i(TAG, "Rewarded ad completed for $placementId with state: $state")

                        if (state == UnityAds.UnityAdsShowCompletionState.COMPLETED) {
                            // Validate single reward per session
                            val currentSession = activeSessionId ?: sessionId
                            if (rewardedSessions.add(currentSession)) {
                                _totalDeliveredRewards.value += 1
                                _lastStatusMessage.value = "Reward verified! +$rewardAmount Diamonds"
                                Log.i(TAG, "Reward granted: +$rewardAmount diamonds for session $currentSession")
                                onRewardEarned(rewardAmount)
                            } else {
                                Log.w(TAG, "Duplicate reward callback detected for session $currentSession. Suppressed.")
                            }
                        } else {
                            // Skipped: Do NOT grant reward
                            _lastStatusMessage.value = "Ad was skipped. No reward granted."
                            Log.d(TAG, "Ad skipped without completion. No reward granted.")
                            onAdDismissed?.invoke()
                        }
                        // Preload next rewarded ad
                        loadRewardedAd(activity.applicationContext)
                    }

                    override fun onUnityAdsShowFailure(
                        placementId: String,
                        error: UnityAds.UnityAdsShowError,
                        message: String
                    ) {
                        isShowingAd.set(false)
                        _isAdReady.value = false
                        _adLoadStatus.value = AdLoadStatus.FAILED
                        _lastStatusMessage.value = "Ad display failed: $message"
                        Log.e(TAG, "Rewarded ad show failed for $placementId: $error - $message")
                        // Never grant reward on failure
                        onAdFailed(message.ifEmpty { "Failed to show ad: $error" })
                        // Attempt to reload next ad
                        loadRewardedAd(activity.applicationContext)
                    }
                }
            )
        } catch (e: Exception) {
            isShowingAd.set(false)
            _isAdReady.value = false
            _lastStatusMessage.value = "Show exception: ${e.message}"
            Log.e(TAG, "Exception showing rewarded ad", e)
            onAdFailed(e.message ?: "Unknown error showing ad")
            loadRewardedAd(activity.applicationContext)
        }
    }

    /**
     * For unit testing and diagnostic reset.
     */
    fun resetForTesting() {
        _sdkStatus.value = SdkStatus.NOT_INITIALIZED
        _adLoadStatus.value = AdLoadStatus.IDLE
        _isAdReady.value = false
        _lastStatusMessage.value = "Reset for testing"
        isShowingAd.set(false)
        activeSessionId = null
        rewardedSessions.clear()
        _totalDeliveredRewards.value = 0
    }

    /**
     * Helper method to simulate mock listener completion in local JVM tests where
     * native Unity Ads C++ binaries or ad servers are not physically available.
     */
    fun handleMockShowCompletionForTesting(
        sessionId: String,
        state: UnityAds.UnityAdsShowCompletionState,
        rewardAmount: Int,
        onRewardEarned: (Int) -> Unit,
        onAdDismissed: (() -> Unit)?
    ) {
        if (state == UnityAds.UnityAdsShowCompletionState.COMPLETED) {
            if (rewardedSessions.add(sessionId)) {
                _totalDeliveredRewards.value += 1
                onRewardEarned(rewardAmount)
            }
        } else {
            onAdDismissed?.invoke()
        }
    }
}
