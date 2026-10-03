package com.example.ads

import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration

/**
 * Centralized Google AdMob configuration.
 *
 * During development:
 * USE_TEST_ADS = true enables official Google Android Native Test Ads.
 *
 * Before publishing to Google Play:
 * 1. Replace PRODUCTION_NATIVE_AD_UNIT_ID with your real AdMob Native Ad Unit ID.
 * 2. Set USE_TEST_ADS = false.
 * 3. Verify real AdMob App ID in AndroidManifest.xml.
 */
object AdMobConfig {

    private const val TAG = "AdMobConfig"

    /**
     * Official Google Mobile Ads Android Native test ad unit ID.
     */
    const val ADMOB_NATIVE_TEST_AD_UNIT_ID: String = "ca-app-pub-3940256099942544/2247696110"

    /**
     * Production Native Ad Unit ID.
     * TODO: Replace with your real production AdMob Native Ad Unit ID before publishing!
     */
    private const val PRODUCTION_NATIVE_AD_UNIT_ID: String = "ca-app-pub-XXXXXXXXXXXXXXXX/XXXXXXXXXX"

    /**
     * Official Google Mobile Ads Android Rewarded Video test ad unit ID.
     */
    const val ADMOB_REWARDED_TEST_AD_UNIT_ID: String = "ca-app-pub-3940256099942544/5224354917"

    /**
     * Official Google Mobile Ads Android Rewarded Interstitial test ad unit ID (Interactive test creative with active click-through URL to Google Play/Chrome).
     */
    const val ADMOB_REWARDED_INTERSTITIAL_TEST_AD_UNIT_ID: String = "ca-app-pub-3940256099942544/5354046379"

    /**
     * Production Rewarded Video Ad Unit ID.
     * TODO: Replace with your real production AdMob Rewarded Ad Unit ID before publishing!
     */
    private const val PRODUCTION_REWARDED_AD_UNIT_ID: String = "ca-app-pub-XXXXXXXXXXXXXXXX/XXXXXXXXXX"

    /**
     * Official Google Mobile Ads Android Interstitial test ad unit ID.
     */
    const val ADMOB_INTERSTITIAL_TEST_AD_UNIT_ID: String = "ca-app-pub-3940256099942544/1033173712"

    /**
     * Production Interstitial Ad Unit ID.
     * TODO: Replace with your real production AdMob Interstitial Ad Unit ID before publishing!
     */
    private const val PRODUCTION_INTERSTITIAL_AD_UNIT_ID: String = "ca-app-pub-XXXXXXXXXXXXXXXX/XXXXXXXXXX"

    /**
     * Set to true during development to load test ads safely without policy violations.
     * Set to false for release production builds.
     */
    const val USE_TEST_ADS: Boolean = true

    /**
     * Test Device IDs for testing real production ad unit IDs safely during QA.
     * TODO: Add your real Android test device ID here (found in Logcat during ad requests).
     */
    val TEST_DEVICE_IDS: List<String> = listOf(
        "YOUR_TEST_DEVICE_ID"
    )

    /**
     * Whether MobileAds SDK initialization has completed.
     */
    var isInitialized: Boolean = false
        private set

    private val initCallbacks = mutableListOf<() -> Unit>()

    fun onInitialized(callback: () -> Unit) {
        if (isInitialized) {
            if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
                callback()
            } else {
                android.os.Handler(android.os.Looper.getMainLooper()).post(callback)
            }
        } else {
            synchronized(initCallbacks) {
                if (isInitialized) {
                    android.os.Handler(android.os.Looper.getMainLooper()).post(callback)
                } else {
                    initCallbacks.add(callback)
                }
            }
        }
    }

    /**
     * Returns the appropriate Native Ad Unit ID based on the test/production flag.
     */
    fun getNativeAdUnitId(): String {
        return if (USE_TEST_ADS) {
            ADMOB_NATIVE_TEST_AD_UNIT_ID
        } else {
            PRODUCTION_NATIVE_AD_UNIT_ID
        }
    }

    /**
     * Returns the appropriate Rewarded Ad Unit ID based on the test/production flag.
     * In test mode, returns Google's interactive rewarded unit with live click redirect to Chrome/Play Store.
     */
    fun getRewardedAdUnitId(): String {
        return if (USE_TEST_ADS) {
            ADMOB_REWARDED_INTERSTITIAL_TEST_AD_UNIT_ID
        } else {
            PRODUCTION_REWARDED_AD_UNIT_ID
        }
    }

    /**
     * Returns the appropriate Interstitial Ad Unit ID based on the test/production flag.
     */
    fun getInterstitialAdUnitId(): String {
        return if (USE_TEST_ADS) {
            ADMOB_INTERSTITIAL_TEST_AD_UNIT_ID
        } else {
            PRODUCTION_INTERSTITIAL_AD_UNIT_ID
        }
    }

    /**
     * Initializes the Google Mobile Ads SDK once at application startup.
     */
    fun initialize(context: Context) {
        try {
            val requestConfigBuilder = RequestConfiguration.Builder()
            // Do NOT register test device IDs in RequestConfiguration to prevent the AdMob SDK
            // from triggering the "AdMob native ad validator" popup overlay during native ad loading
            requestConfigBuilder.setTestDeviceIds(emptyList())
            MobileAds.setRequestConfiguration(requestConfigBuilder.build())

            MobileAds.initialize(context) { status ->
                isInitialized = true
                Log.d(TAG, "Google Mobile Ads SDK initialized successfully: ${status.adapterStatusMap}")
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    synchronized(initCallbacks) {
                        initCallbacks.forEach { it.invoke() }
                        initCallbacks.clear()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing MobileAds SDK: ${e.message}", e)
        }
    }
}
