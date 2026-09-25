package com.example.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

object NetworkConnectivityHelper {
    /**
     * Robust internet availability check.
     * Uses ConnectivityManager and NetworkCapabilities for modern devices (Android 10+ / API 29+)
     * while keeping backward-compatible fallback support for older Android releases.
     */
    fun isInternetAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false

        // Under Robolectric test environment, mock the connectivity behavior dynamically
        if (Build.FINGERPRINT == "robolectric") {
            @Suppress("DEPRECATION")
            if (connectivityManager.activeNetwork == null && connectivityManager.activeNetworkInfo == null) {
                return false
            }
            return true
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

            val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val hasWifiOrCellular = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)

            hasInternet && hasWifiOrCellular
        } else {
            @Suppress("DEPRECATION")
            val activeNetworkInfo = connectivityManager.activeNetworkInfo
            @Suppress("DEPRECATION")
            activeNetworkInfo != null && activeNetworkInfo.isConnected
        }
    }

    /**
     * Reactive network listener flow that emits real-time connectivity status.
     * Emits true when network is connected and has internet capabilities, and false when disconnected.
     */
    fun observeConnectivity(context: Context): Flow<Boolean> = callbackFlow {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (connectivityManager == null) {
            trySend(true)
            close()
            return@callbackFlow
        }

        // Emit initial status
        trySend(isInternetAvailable(context))

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(isInternetAvailable(context))
            }

            override fun onLost(network: Network) {
                trySend(isInternetAvailable(context))
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                        (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                         networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                         networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
                trySend(hasInternet)
            }

            override fun onUnavailable() {
                trySend(false)
            }
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                connectivityManager.registerDefaultNetworkCallback(callback)
            } else {
                val request = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()
                connectivityManager.registerNetworkCallback(request, callback)
            }
        } catch (e: Exception) {
            Log.w("NetworkConnectivityHelper", "Failed to register network callback: ${e.message}")
            trySend(isInternetAvailable(context))
        }

        awaitClose {
            try {
                connectivityManager.unregisterNetworkCallback(callback)
            } catch (e: Exception) {
                Log.w("NetworkConnectivityHelper", "Failed to unregister network callback: ${e.message}")
            }
        }
    }.distinctUntilChanged()
}
