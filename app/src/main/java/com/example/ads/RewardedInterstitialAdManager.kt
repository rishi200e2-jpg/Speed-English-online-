package com.example.ads

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Sequential Dual-Ad & Rewarded Ad Manager:
 * Handles Rewarded Video Ads, Rewarded Interstitial Ads, and Interstitial Ads with full click-through support,
 * graceful fallbacks, and watchdog timers.
 *
 * Ensures that when a user clicks on a Rewarded Ad, the destination browser or Google Play Store
 * opens immediately without being blocked or interrupted by subsequent ads.
 */
object RewardedInterstitialAdManager {

    private const val TAG = "RewardedInterstitialMgr"

    @Volatile
    private var cachedRewardedAd: RewardedAd? = null
    @Volatile
    private var cachedRewardedInterstitialAd: RewardedInterstitialAd? = null
    @Volatile
    private var isRewardedLoading: Boolean = false

    @Volatile
    private var cachedInterstitialAd: InterstitialAd? = null
    @Volatile
    private var isInterstitialLoading: Boolean = false

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Preloads a Rewarded Ad in the background.
     * Uses Activity context when available so click-through intents and Custom Tabs open correctly.
     */
    fun preloadRewardedAd(context: Context) {
        if (cachedRewardedAd != null || cachedRewardedInterstitialAd != null || isRewardedLoading) {
            return
        }

        isRewardedLoading = true
        AdMobConfig.onInitialized {
            val adUnitId = AdMobConfig.getRewardedAdUnitId()
            val request = AdRequest.Builder().build()
            val loadContext = context.findActivity() ?: context

            Log.d(TAG, "Preloading Rewarded Ad ($adUnitId)...")

            // Try loading as RewardedInterstitialAd first (interactive with live click redirects)
            RewardedInterstitialAd.load(
                loadContext,
                adUnitId,
                request,
                object : RewardedInterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedInterstitialAd) {
                        isRewardedLoading = false
                        cachedRewardedInterstitialAd = ad
                        Log.d(TAG, "✅ Rewarded Interstitial Ad preloaded and ready for display & clicks!")
                    }

                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        Log.d(TAG, "Rewarded Interstitial not loaded (${loadAdError.message}), trying standard RewardedAd...")
                        // Fallback to standard RewardedAd
                        RewardedAd.load(
                            loadContext,
                            adUnitId,
                            request,
                            object : RewardedAdLoadCallback() {
                                override fun onAdLoaded(rewardedAd: RewardedAd) {
                                    isRewardedLoading = false
                                    cachedRewardedAd = rewardedAd
                                    Log.d(TAG, "✅ Rewarded Ad preloaded and ready!")
                                }

                                override fun onAdFailedToLoad(error: LoadAdError) {
                                    isRewardedLoading = false
                                    Log.w(TAG, "❌ Rewarded Ad failed to load: ${error.message} (Code: ${error.code})")
                                }
                            }
                        )
                    }
                }
            )
        }
    }

    /**
     * Preloads an Interstitial Ad in the background.
     */
    fun preloadInterstitialAd(context: Context) {
        if (cachedInterstitialAd != null || isInterstitialLoading) {
            return
        }

        isInterstitialLoading = true
        AdMobConfig.onInitialized {
            val adUnitId = AdMobConfig.getInterstitialAdUnitId()
            val request = AdRequest.Builder().build()
            val loadContext = context.findActivity() ?: context

            Log.d(TAG, "Preloading Interstitial Ad ($adUnitId)...")
            InterstitialAd.load(
                loadContext,
                adUnitId,
                request,
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(interstitialAd: InterstitialAd) {
                        isInterstitialLoading = false
                        cachedInterstitialAd = interstitialAd
                        Log.d(TAG, "✅ Interstitial Ad preloaded and ready!")
                    }

                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        isInterstitialLoading = false
                        Log.w(TAG, "❌ Interstitial Ad failed to load: ${loadAdError.message} (Code: ${loadAdError.code})")
                    }
                }
            )
        }
    }

    /**
     * Preloads both Rewarded and Interstitial ads simultaneously.
     */
    fun preloadAll(context: Context) {
        preloadRewardedAd(context)
        preloadInterstitialAd(context)
    }

    /**
     * Shows a standalone Rewarded Ad with full click support and guaranteed action execution.
     */
    fun showRewardedAd(
        activity: Activity?,
        onCompleted: () -> Unit
    ) {
        val hasCompleted = AtomicBoolean(false)
        val safeFinalAction = {
            if (hasCompleted.compareAndSet(false, true)) {
                mainHandler.post {
                    try {
                        onCompleted()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error executing onCompleted: ${e.message}", e)
                    }
                }
            }
        }

        if (activity == null || activity.isFinishing || activity.isDestroyed) {
            safeFinalAction()
            return
        }

        preloadAll(activity)

        val rewardedInterstitial = cachedRewardedInterstitialAd
        if (rewardedInterstitial != null) {
            cachedRewardedInterstitialAd = null
            preloadRewardedAd(activity)

            rewardedInterstitial.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdClicked() {
                    Log.d(TAG, "🎯 Rewarded Interstitial Ad clicked! User is navigating to destination.")
                }

                override fun onAdShowedFullScreenContent() {
                    Log.d(TAG, "✅ Rewarded Interstitial Ad displayed full screen.")
                }

                override fun onAdImpression() {
                    Log.d(TAG, "📊 Rewarded Interstitial Ad impression recorded.")
                }

                override fun onAdDismissedFullScreenContent() {
                    Log.d(TAG, "✅ Rewarded Interstitial Ad dismissed. Executing action...")
                    safeFinalAction()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    Log.w(TAG, "❌ Rewarded Interstitial Ad failed to show: ${adError.message}")
                    safeFinalAction()
                }
            }

            try {
                rewardedInterstitial.show(activity) { rewardItem ->
                    Log.d(TAG, "🎁 Rewarded item earned: ${rewardItem.amount} ${rewardItem.type}")
                }
                return
            } catch (e: Exception) {
                Log.e(TAG, "Exception showing Rewarded Interstitial ad: ${e.message}", e)
                safeFinalAction()
                return
            }
        }

        val rewarded = cachedRewardedAd
        if (rewarded != null) {
            cachedRewardedAd = null
            preloadRewardedAd(activity)

            rewarded.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdClicked() {
                    Log.d(TAG, "🎯 Rewarded Ad clicked! User is opening external ad destination.")
                }

                override fun onAdShowedFullScreenContent() {
                    Log.d(TAG, "✅ Rewarded Ad displayed full screen.")
                }

                override fun onAdImpression() {
                    Log.d(TAG, "📊 Rewarded Ad impression logged.")
                }

                override fun onAdDismissedFullScreenContent() {
                    Log.d(TAG, "✅ Rewarded Ad dismissed. Executing action...")
                    safeFinalAction()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    Log.w(TAG, "❌ Rewarded Ad failed to show: ${adError.message}")
                    safeFinalAction()
                }
            }

            try {
                rewarded.show(activity) { rewardItem ->
                    Log.d(TAG, "🎁 Rewarded item earned: ${rewardItem.amount} ${rewardItem.type}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception showing Rewarded ad: ${e.message}", e)
                safeFinalAction()
            }
        } else {
            safeFinalAction()
        }
    }

    /**
     * Dual Ad Sequence:
     * 1. Displays Rewarded Video Ad (Ad #1).
     * 2. When user clicks the ad, immediately lets the user view the ad destination without interrupting.
     * 3. If dismissed without clicking, transitions smoothly to Interstitial Ad (Ad #2).
     * 4. When completed, executes [onCompleted].
     */
    fun showRewardedThenInterstitial(
        activity: Activity?,
        onCompleted: () -> Unit
    ) {
        val hasCompleted = AtomicBoolean(false)
        val isRewardedClicked = AtomicBoolean(false)
        val safeFinalAction = {
            if (hasCompleted.compareAndSet(false, true)) {
                mainHandler.post {
                    try {
                        onCompleted()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error executing final button action: ${e.message}", e)
                    }
                }
            }
        }

        if (activity == null || activity.isFinishing || activity.isDestroyed) {
            safeFinalAction()
            return
        }

        // Trigger background preloads
        preloadAll(activity)

        // Step 2: Show Interstitial Ad (Ad #2)
        fun showInterstitialStep() {
            if (activity.isFinishing || activity.isDestroyed || isRewardedClicked.get()) {
                safeFinalAction()
                return
            }

            fun presentInterstitial(): Boolean {
                val interstitial = cachedInterstitialAd
                if (interstitial != null) {
                    cachedInterstitialAd = null
                    preloadInterstitialAd(activity)

                    interstitial.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdClicked() {
                            Log.d(TAG, "🎯 Ad #2 (Interstitial) clicked! Opening destination...")
                        }

                        override fun onAdShowedFullScreenContent() {
                            Log.d(TAG, "✅ Ad #2 (Interstitial) showed full screen.")
                        }

                        override fun onAdImpression() {
                            Log.d(TAG, "📊 Ad #2 (Interstitial) impression recorded.")
                        }

                        override fun onAdDismissedFullScreenContent() {
                            Log.d(TAG, "✅ Ad #2 (Interstitial) dismissed. Performing button action...")
                            safeFinalAction()
                        }

                        override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                            Log.w(TAG, "❌ Ad #2 (Interstitial) failed to show: ${adError.message}")
                            safeFinalAction()
                        }
                    }

                    try {
                        interstitial.show(activity)
                        return true
                    } catch (e: Exception) {
                        Log.e(TAG, "Exception showing Interstitial ad: ${e.message}", e)
                    }
                }
                return false
            }

            if (presentInterstitial()) {
                return
            }

            // If Interstitial is currently loading, wait up to 1500ms
            if (isInterstitialLoading) {
                Log.d(TAG, "Waiting for Interstitial ad to finish loading...")
                var checkCount = 0
                val maxChecks = 15 // 15 * 100ms = 1500ms
                val checkRunnable = object : Runnable {
                    override fun run() {
                        checkCount++
                        if (presentInterstitial()) {
                            return
                        }
                        if (checkCount < maxChecks && isInterstitialLoading) {
                            mainHandler.postDelayed(this, 100)
                        } else {
                            Log.d(TAG, "Interstitial load timeout/unavailable. Executing button action directly.")
                            safeFinalAction()
                        }
                    }
                }
                mainHandler.postDelayed(checkRunnable, 100)
            } else {
                safeFinalAction()
            }
        }

        // Step 1: Show Rewarded Ad (Ad #1)
        fun presentRewarded(): Boolean {
            val rewardedInterstitial = cachedRewardedInterstitialAd
            if (rewardedInterstitial != null) {
                cachedRewardedInterstitialAd = null
                preloadRewardedAd(activity)

                rewardedInterstitial.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdClicked() {
                        Log.d(TAG, "🎯 Ad #1 (Rewarded Interstitial) CLICKED! Launching destination...")
                        isRewardedClicked.set(true)
                    }

                    override fun onAdShowedFullScreenContent() {
                        Log.d(TAG, "✅ Ad #1 (Rewarded Interstitial) showed full screen.")
                    }

                    override fun onAdImpression() {
                        Log.d(TAG, "📊 Ad #1 (Rewarded Interstitial) impression recorded.")
                    }

                    override fun onAdDismissedFullScreenContent() {
                        Log.d(TAG, "✅ Ad #1 dismissed. (Clicked: ${isRewardedClicked.get()})")
                        if (isRewardedClicked.get()) {
                            safeFinalAction()
                        } else {
                            mainHandler.postDelayed({
                                showInterstitialStep()
                            }, 350)
                        }
                    }

                    override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                        Log.w(TAG, "❌ Ad #1 failed to show: ${adError.message}. Moving to Interstitial...")
                        mainHandler.postDelayed({
                            showInterstitialStep()
                        }, 200)
                    }
                }

                try {
                    rewardedInterstitial.show(activity) { rewardItem ->
                        Log.d(TAG, "Rewarded video completed: earned ${rewardItem.amount} ${rewardItem.type}")
                    }
                    return true
                } catch (e: Exception) {
                    Log.e(TAG, "Exception showing Rewarded ad: ${e.message}", e)
                }
            }

            val rewarded = cachedRewardedAd
            if (rewarded != null) {
                cachedRewardedAd = null
                preloadRewardedAd(activity)

                rewarded.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdClicked() {
                        Log.d(TAG, "🎯 Ad #1 (Rewarded) CLICKED! Launching destination...")
                        isRewardedClicked.set(true)
                    }

                    override fun onAdShowedFullScreenContent() {
                        Log.d(TAG, "✅ Ad #1 (Rewarded) showed full screen.")
                    }

                    override fun onAdImpression() {
                        Log.d(TAG, "📊 Ad #1 (Rewarded) impression recorded.")
                    }

                    override fun onAdDismissedFullScreenContent() {
                        Log.d(TAG, "✅ Ad #1 dismissed. (Clicked: ${isRewardedClicked.get()})")
                        if (isRewardedClicked.get()) {
                            safeFinalAction()
                        } else {
                            mainHandler.postDelayed({
                                showInterstitialStep()
                            }, 350)
                        }
                    }

                    override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                        Log.w(TAG, "❌ Ad #1 failed to show: ${adError.message}. Moving to Interstitial...")
                        mainHandler.postDelayed({
                            showInterstitialStep()
                        }, 200)
                    }
                }

                try {
                    rewarded.show(activity) { rewardItem ->
                        Log.d(TAG, "Rewarded video completed: earned ${rewardItem.amount} ${rewardItem.type}")
                    }
                    return true
                } catch (e: Exception) {
                    Log.e(TAG, "Exception showing Rewarded ad: ${e.message}", e)
                }
            }
            return false
        }

        // Try presenting Rewarded Ad immediately
        if (presentRewarded()) {
            return
        }

        // If Rewarded Ad is currently loading, wait up to 1500ms
        if (isRewardedLoading) {
            Log.d(TAG, "Waiting for Rewarded ad to finish loading...")
            var checkCount = 0
            val maxChecks = 15 // 15 * 100ms = 1500ms
            val checkRunnable = object : Runnable {
                override fun run() {
                    checkCount++
                    if (presentRewarded()) {
                        return
                    }
                    if (checkCount < maxChecks && isRewardedLoading) {
                        mainHandler.postDelayed(this, 100)
                    } else {
                        Log.d(TAG, "Rewarded ad load timeout. Moving to Ad #2 (Interstitial)...")
                        showInterstitialStep()
                    }
                }
            }
            mainHandler.postDelayed(checkRunnable, 100)
        } else {
            showInterstitialStep()
        }
    }

    /**
     * Dedicated Back Navigation Ad Handler:
     * Detects back button press, tries to show preloaded Rewarded / Interstitial Ad with
     * strict fallback mechanisms so back navigation is NEVER stuck or failed under any condition.
     */
    fun showBackNavigationAd(
        activity: Activity?,
        onCompleted: () -> Unit
    ) {
        val hasCompleted = AtomicBoolean(false)
        val safeFinalAction = {
            if (hasCompleted.compareAndSet(false, true)) {
                mainHandler.post {
                    try {
                        onCompleted()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error executing back navigation action: ${e.message}", e)
                    }
                }
            }
        }

        if (activity == null || activity.isFinishing || activity.isDestroyed) {
            safeFinalAction()
            return
        }

        // Trigger background preloads for next navigation step
        preloadAll(activity)

        // Safety watchdog timer: guarantees back navigation executes within 2500ms max under all scenarios
        mainHandler.postDelayed({
            if (!hasCompleted.get()) {
                Log.w(TAG, "⏱️ Back ad safety watchdog timeout. Executing back action directly.")
                safeFinalAction()
            }
        }, 2500)

        try {
            showRewardedThenInterstitial(activity) {
                safeFinalAction()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception displaying back ad sequence: ${e.message}", e)
            safeFinalAction()
        }
    }

    /**
     * Cleans up all cached ads to release memory.
     */
    fun destroyAll() {
        cachedRewardedAd = null
        cachedRewardedInterstitialAd = null
        cachedInterstitialAd = null
    }
}

/**
 * Extension function to safely extract the hosting [Activity] from a Compose [Context].
 */
fun Context.findActivity(): Activity? {
    var currentContext: Context? = this
    while (currentContext is ContextWrapper) {
        if (currentContext is Activity) {
            return currentContext
        }
        currentContext = currentContext.baseContext
    }
    return null
}
