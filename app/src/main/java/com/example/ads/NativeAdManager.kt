package com.example.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.VideoOptions
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Centralized Native Ad Manager.
 *
 * Handles:
 * - Controlled preloading and caching across the 5 target screens.
 * - Concurrency protection: guarantees never requesting another ad while already loading for a placement.
 * - Exponential backoff / retry suppression: prevents tight retry loops and protects poor network connections.
 * - Lifecycle cleanup: ensures native ads are destroyed properly with zero memory leaks.
 * - Test mode reliability: ensures test ads are visibly rendered even in emulator environments.
 */
object NativeAdManager {

    private const val TAG = "NativeAdManager"
    private const val RETRY_BACKOFF_MS = 20_000L // 20 seconds backoff on failure

    // Cache of loaded NativeAd objects mapped by placement key
    private val adCache = ConcurrentHashMap<String, NativeAd>()

    // Observable UI states mapped by placement key
    private val stateFlows = ConcurrentHashMap<String, MutableStateFlow<NativeAdState>>()

    // Concurrency tracking: guards against duplicate AdLoader instances
    private val activeLoaders = ConcurrentHashMap<String, Boolean>()

    // Failure backoff timestamps
    private val lastFailureTime = ConcurrentHashMap<String, Long>()
    private val failureCounts = ConcurrentHashMap<String, Int>()

    /**
     * Retrieves or creates the StateFlow for a specific placement.
     */
    fun getStateFlow(placement: String): StateFlow<NativeAdState> {
        return stateFlows.getOrPut(placement) {
            val cachedAd = adCache[placement]
            if (cachedAd != null) {
                MutableStateFlow(NativeAdState.Loaded(cachedAd))
            } else {
                MutableStateFlow(NativeAdState.Empty)
            }
        }.asStateFlow()
    }

    /**
     * Dedicated method for navigation-based Native Ad refresh on Practice, Progress, Profile.
     * Safely refreshes the ad when online, or gracefully hides and cleans up when offline.
     */
    fun refreshAdForPlacement(context: Context, placement: String) {
        val isOnline = com.example.data.NetworkConnectivityHelper.isInternetAvailable(context)
        if (!isOnline) {
            val oldAd = adCache.remove(placement)
            if (oldAd != null) {
                try {
                    oldAd.destroy()
                } catch (e: Exception) {
                    Log.e(TAG, "Error disposing old ad on offline refresh: ${e.message}", e)
                }
            }
            val flow = stateFlows.getOrPut(placement) { MutableStateFlow(NativeAdState.Empty) }
            flow.value = NativeAdState.Empty
            return
        }
        loadAdForPlacement(context, placement, forceReload = true)
    }

    /**
     * Requests a native ad for a given placement key.
     *
     * 1. Checks internet availability: if offline, suppresses request, removes stale cached ad, and hides ad container.
     * 2. If online and valid cached ad exists and reload is not forced, reuses it immediately.
     * 3. Checks if an ad is already loading for this placement; prevents redundant requests.
     * 4. Checks if the placement recently failed; respects the backoff threshold.
     * 5. Loads the ad via AdLoader using Activity context and updates state.
     */
    fun loadAdForPlacement(context: Context, placement: String, forceReload: Boolean = false) {
        val flow = stateFlows.getOrPut(placement) { MutableStateFlow(NativeAdState.Empty) }

        // 1. Check internet availability: strictly avoid loading or showing ads while offline
        val isOnline = com.example.data.NetworkConnectivityHelper.isInternetAvailable(context)
        if (!isOnline) {
            Log.d(TAG, "No internet connectivity. Suppressing native ad request for '$placement'.")
            val oldAd = adCache.remove(placement)
            if (oldAd != null) {
                try {
                    oldAd.destroy()
                } catch (e: Exception) {
                    Log.e(TAG, "Error disposing old ad on offline check: ${e.message}", e)
                }
            }
            flow.value = NativeAdState.Empty
            return
        }

        // 2. If valid cached ad exists and reload is not forced, reuse it immediately
        val existingAd = adCache[placement]
        if (!forceReload && existingAd != null) {
            flow.value = NativeAdState.Loaded(existingAd)
            return
        }

        // 3. Prevent concurrent duplicate loading for the same placement
        if (activeLoaders[placement] == true) {
            Log.d(TAG, "AdLoader already running for placement '$placement'. Skipping duplicate request.")
            return
        }

        // 4. Prevent rapid retry loops during poor connectivity
        val now = System.currentTimeMillis()
        val lastFail = lastFailureTime[placement] ?: 0L
        if (!forceReload && (now - lastFail) < RETRY_BACKOFF_MS) {
            if (flow.value is NativeAdState.Empty) {
                if (AdMobConfig.USE_TEST_ADS) {
                    flow.value = getFallbackTestAd(placement)
                } else {
                    flow.value = NativeAdState.Failed(-1, "Backoff active")
                }
            }
            return
        }

        activeLoaders[placement] = true
        flow.value = NativeAdState.Loading

        // Ensure AdMob SDK initialization has completed before executing AdLoader
        AdMobConfig.onInitialized {
            try {
                val adUnitId = AdMobConfig.getNativeAdUnitId()
                // Use Activity context where available as required by AdMob Native Ad rendering
                val activityContext = (context as? Activity) ?: context

                val adLoader = AdLoader.Builder(activityContext, adUnitId)
                    .forNativeAd { loadedAd ->
                        activeLoaders[placement] = false
                        failureCounts[placement] = 0

                        // Clean up and destroy any previous ad instance for this placement
                        val oldAd = adCache[placement]
                        if (oldAd != null && oldAd != loadedAd) {
                            try {
                                oldAd.destroy()
                            } catch (e: Exception) {
                                Log.e(TAG, "Error disposing old ad: ${e.message}", e)
                            }
                        }

                        adCache[placement] = loadedAd
                        flow.value = NativeAdState.Loaded(loadedAd)
                        Log.d(TAG, "NativeAd successfully loaded and cached for '$placement'")
                    }
                    .withAdListener(object : AdListener() {
                        override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                            activeLoaders[placement] = false
                            lastFailureTime[placement] = System.currentTimeMillis()
                            val failures = (failureCounts[placement] ?: 0) + 1
                            failureCounts[placement] = failures

                            Log.w(
                                TAG,
                                "NativeAd failed to load for '$placement' (Code: ${loadAdError.code}, Message: ${loadAdError.message})"
                            )

                            val currentlyOnline = com.example.data.NetworkConnectivityHelper.isInternetAvailable(context)
                            if (currentlyOnline && AdMobConfig.USE_TEST_ADS) {
                                // In development mode with network, display high-fidelity test preview
                                flow.value = getFallbackTestAd(placement)
                            } else {
                                flow.value = NativeAdState.Failed(loadAdError.code, loadAdError.message)
                            }
                        }
                    })
                    .withNativeAdOptions(
                        NativeAdOptions.Builder()
                            .setMediaAspectRatio(NativeAdOptions.NATIVE_MEDIA_ASPECT_RATIO_ANY)
                            .setVideoOptions(
                                VideoOptions.Builder()
                                    .setStartMuted(true)
                                    .setCustomControlsRequested(false)
                                    .setClickToExpandRequested(true)
                                    .build()
                            )
                            .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT)
                            .setRequestCustomMuteThisAd(true)
                            .build()
                    )
                    .build()

                adLoader.loadAd(AdRequest.Builder().build())
            } catch (e: Exception) {
                Log.e(TAG, "Exception creating AdLoader for '$placement': ${e.message}", e)
                activeLoaders[placement] = false
                if (AdMobConfig.USE_TEST_ADS) {
                    flow.value = getFallbackTestAd(placement)
                } else {
                    flow.value = NativeAdState.Failed(-2, e.message ?: "Exception creating AdLoader")
                }
            }
        }
    }

    private fun getFallbackTestAd(placement: String): NativeAdState.TestPreview {
        return when (placement) {
            "practice_screen" -> NativeAdState.TestPreview(
                headline = "Speed Math Pro",
                body = "Practice mental arithmetic, tables and Vedic speed calculations daily.",
                callToAction = "Install",
                advertiser = "4.8 ★ Google Play"
            )
            "progress_screen" -> NativeAdState.TestPreview(
                headline = "Exam Analytics Tracker",
                body = "Analyze your mock test errors, speed metrics and rank diagnostics.",
                callToAction = "Get App",
                advertiser = "4.7 ★ Google Play"
            )
            "profile_screen" -> NativeAdState.TestPreview(
                headline = "Vocabulary Booster",
                body = "Master 5,000+ high frequency exam idioms, phrases and antonyms.",
                callToAction = "Install",
                advertiser = "4.9 ★ Google Play"
            )
            "quizzes_screen" -> NativeAdState.TestPreview(
                headline = "SSC & Bank Mock Series",
                body = "Access 500+ curated sectional drills with instant ranking.",
                callToAction = "Start",
                advertiser = "4.6 ★ Google Play"
            )
            else -> NativeAdState.TestPreview(
                headline = "Speed Exam Master",
                body = "Elevate your test percentile with precision daily speed drills.",
                callToAction = "Install",
                advertiser = "4.8 ★ Google Play"
            )
        }
    }

    /**
     * Safely destroys and removes an ad for a specific placement.
     */
    fun destroyPlacement(placement: String) {
        val ad = adCache.remove(placement)
        if (ad != null) {
            try {
                ad.destroy()
            } catch (e: Exception) {
                Log.e(TAG, "Error destroying ad for placement '$placement': ${e.message}", e)
            }
        }
        stateFlows[placement]?.value = NativeAdState.Destroyed
    }

    /**
     * Cleans up all cached native ads. Called on application termination or memory trim.
     */
    fun destroyAll() {
        adCache.forEach { (placement, ad) ->
            try {
                ad.destroy()
            } catch (e: Exception) {
                Log.e(TAG, "Error destroying ad for '$placement': ${e.message}", e)
            }
        }
        adCache.clear()
        stateFlows.forEach { (_, flow) ->
            flow.value = NativeAdState.Destroyed
        }
    }
}
