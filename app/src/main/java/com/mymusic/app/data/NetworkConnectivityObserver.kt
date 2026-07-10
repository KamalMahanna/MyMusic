package com.mymusic.app.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Reactive network connectivity observer using [ConnectivityManager.registerDefaultNetworkCallback].
 * Exposes a [StateFlow] that emits `true` when the device has internet access
 * and `false` otherwise.
 *
 * Uses the default network callback which tracks the system's preferred network:
 * - [onAvailable]: A default network exists → connected
 * - [onLost]: No default network remains → disconnected (set false directly, don't re-query)
 * - [onCapabilitiesChanged]: Check VALIDATED to catch networks that lose real internet
 */
class NetworkConnectivityObserver(context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _isConnected = MutableStateFlow(checkCurrentConnectivity())
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Log.d(TAG, "onAvailable: network=$network → connected")
            _isConnected.value = true
        }

        override fun onLost(network: Network) {
            // registerDefaultNetworkCallback: onLost means NO default network remains.
            // Set false directly — don't re-query activeNetwork which can be stale.
            Log.d(TAG, "onLost: network=$network → disconnected")
            _isConnected.value = false
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            val validated = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            Log.d(TAG, "onCapabilitiesChanged: validated=$validated")
            _isConnected.value = validated
        }
    }

    init {
        Log.d(TAG, "init: initial isConnected=${_isConnected.value}")
        try {
            connectivityManager.registerDefaultNetworkCallback(networkCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register network callback", e)
        }
    }

    private fun checkCurrentConnectivity(): Boolean {
        val activeNetwork = connectivityManager.activeNetwork
        if (activeNetwork == null) {
            Log.d(TAG, "checkCurrentConnectivity: no active network → false")
            return false
        }
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        if (capabilities == null) {
            Log.d(TAG, "checkCurrentConnectivity: no capabilities → false")
            return false
        }
        // Check both: INTERNET (can reach internet) + VALIDATED (system confirmed it works)
        val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val result = hasInternet && validated
        Log.d(TAG, "checkCurrentConnectivity: internet=$hasInternet, validated=$validated → $result")
        return result
    }

    companion object {
        private const val TAG = "NetworkConnectivity"
    }
}
