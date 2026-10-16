package com.example.ads

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.GameStorage
import com.unity3d.ads.UnityAds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UnityAdsTest {
    private lateinit var context: Context
    private lateinit var gameStorage: GameStorage

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        gameStorage = GameStorage(context)
        UnityAdsManager.resetForTesting()
    }

    @Test
    fun testUnityAdsConfiguration() {
        assertEquals("800399465", UnityAdsManager.GAME_ID)
        assertEquals("BP_Rewarded_Android", UnityAdsManager.REWARDED_PLACEMENT_ID)
        assertFalse("Production testMode must be disabled for live ads", UnityAdsManager.testMode)
    }

    @Test
    fun testSdkInitializationInitialState() {
        assertEquals(UnityAdsManager.SdkStatus.NOT_INITIALIZED, UnityAdsManager.sdkStatus.value)
        assertFalse(UnityAdsManager.isAdReady.value)
        assertEquals(UnityAdsManager.AdLoadStatus.IDLE, UnityAdsManager.adLoadStatus.value)
    }

    @Test
    fun testSuccessfulRewardedAdCompletionGrantsReward() {
        val initialDiamonds = gameStorage.getStats().totalDiamonds
        var rewardCallbackInvoked = false
        var rewardedAmountReceived = 0
        val sessionId = UUID.randomUUID().toString()

        UnityAdsManager.handleMockShowCompletionForTesting(
            sessionId = sessionId,
            state = UnityAds.UnityAdsShowCompletionState.COMPLETED,
            rewardAmount = 50,
            onRewardEarned = { amount ->
                rewardCallbackInvoked = true
                rewardedAmountReceived = amount
                gameStorage.addDiamonds(amount)
            },
            onAdDismissed = {}
        )

        assertTrue("onRewardEarned callback must be invoked on COMPLETED state", rewardCallbackInvoked)
        assertEquals(50, rewardedAmountReceived)
        assertEquals(1, UnityAdsManager.totalDeliveredRewards.value)

        val updatedDiamonds = gameStorage.getStats().totalDiamonds
        assertEquals(initialDiamonds + 50, updatedDiamonds)
    }

    @Test
    fun testSkippedAdDoesNotGrantReward() {
        val initialDiamonds = gameStorage.getStats().totalDiamonds
        var rewardCallbackInvoked = false
        var dismissedCallbackInvoked = false
        val sessionId = UUID.randomUUID().toString()

        UnityAdsManager.handleMockShowCompletionForTesting(
            sessionId = sessionId,
            state = UnityAds.UnityAdsShowCompletionState.SKIPPED,
            rewardAmount = 50,
            onRewardEarned = {
                rewardCallbackInvoked = true
                gameStorage.addDiamonds(50)
            },
            onAdDismissed = {
                dismissedCallbackInvoked = true
            }
        )

        assertFalse("Reward must NOT be granted when ad is skipped", rewardCallbackInvoked)
        assertTrue("Dismiss callback should be invoked when ad is skipped", dismissedCallbackInvoked)
        assertEquals(0, UnityAdsManager.totalDeliveredRewards.value)
        assertEquals("Diamonds balance must remain unchanged", initialDiamonds, gameStorage.getStats().totalDiamonds)
    }

    @Test
    fun testDuplicateRewardPreventionPerSession() {
        val initialDiamonds = gameStorage.getStats().totalDiamonds
        var rewardCallCount = 0
        val fixedSessionId = "session-unique-xyz-123"

        UnityAdsManager.handleMockShowCompletionForTesting(
            sessionId = fixedSessionId,
            state = UnityAds.UnityAdsShowCompletionState.COMPLETED,
            rewardAmount = 25,
            onRewardEarned = { amount ->
                rewardCallCount++
                gameStorage.addDiamonds(amount)
            },
            onAdDismissed = {}
        )

        assertEquals("First completion must grant reward", 1, rewardCallCount)
        assertEquals(initialDiamonds + 25, gameStorage.getStats().totalDiamonds)

        // Duplicate callback with SAME session ID
        UnityAdsManager.handleMockShowCompletionForTesting(
            sessionId = fixedSessionId,
            state = UnityAds.UnityAdsShowCompletionState.COMPLETED,
            rewardAmount = 25,
            onRewardEarned = { amount ->
                rewardCallCount++
                gameStorage.addDiamonds(amount)
            },
            onAdDismissed = {}
        )

        assertEquals("Duplicate reward callback must be ignored", 1, rewardCallCount)
        assertEquals(1, UnityAdsManager.totalDeliveredRewards.value)
        assertEquals("Diamonds balance must not have duplicate reward", initialDiamonds + 25, gameStorage.getStats().totalDiamonds)
    }
}
