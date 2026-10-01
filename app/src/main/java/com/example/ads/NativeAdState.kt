package com.example.ads

import com.google.android.gms.ads.nativead.NativeAd

/**
 * Lifecycle states for Native Ad display and loading.
 */
sealed interface NativeAdState {
    /**
     * Initial unallocated state or when ads are disabled.
     */
    object Empty : NativeAdState

    /**
     * Loading in progress: lightweight placeholder skeleton displayed to prevent UI jumping.
     */
    object Loading : NativeAdState

    /**
     * Live AdMob ad loaded successfully and ready to bind into the NativeAdView.
     */
    data class Loaded(val nativeAd: NativeAd) : NativeAdState

    /**
     * Development test preview state shown in test mode if network/Play Services is unavailable in emulator.
     */
    data class TestPreview(
        val headline: String = "Speed Drill Master",
        val body: String = "Boost your exam accuracy with speed drills and full length tests.",
        val callToAction: String = "Install",
        val advertiser: String = "4.8 ★ Google Play"
    ) : NativeAdState

    /**
     * Ad failed to load; the container hides gracefully in production without displaying raw error text.
     */
    data class Failed(val errorCode: Int, val errorMessage: String) : NativeAdState

    /**
     * Destroyed/cleared state to prevent memory leaks.
     */
    object Destroyed : NativeAdState
}
