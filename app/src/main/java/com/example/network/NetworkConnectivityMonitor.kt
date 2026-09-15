package com.example.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class NetworkConnectivityMonitor private constructor(context: Context) {
    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val _isConnected = MutableStateFlow(checkInitialConnectivity())
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _connectionType = MutableStateFlow("Unbekannt")
    val connectionType: StateFlow<String> = _connectionType.asStateFlow()

    private val monitorScope = CoroutineScope(Dispatchers.Default)

    init {
        registerNetworkCallback()
    }

    private fun checkInitialConnectivity(): Boolean {
        val cm = connectivityManager ?: return true
        val activeNetwork = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
        val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        updateConnectionType(capabilities)
        return hasInternet
    }

    private fun updateConnectionType(capabilities: NetworkCapabilities?) {
        if (capabilities == null) {
            _connectionType.value = "Keine Verbindung"
            return
        }
        _connectionType.value = when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WLAN"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobilfunk"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "Aktiv"
        }
    }

    private fun registerNetworkCallback() {
        if (connectivityManager == null) return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        try {
            connectivityManager.registerNetworkCallback(
                request,
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        Log.i(TAG, "Network became available. Triggering immediate retry signal.")
                        _isConnected.value = true
                        val caps = connectivityManager.getNetworkCapabilities(network)
                        updateConnectionType(caps)
                        NetworkRetryPolicy.triggerImmediateRetry()
                    }

                    override fun onLost(network: Network) {
                        Log.w(TAG, "Network connection lost.")
                        _isConnected.value = false
                        _connectionType.value = "Getrennt"
                    }

                    override fun onCapabilitiesChanged(
                        network: Network,
                        networkCapabilities: NetworkCapabilities
                    ) {
                        val hasInternet =
                            networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        _isConnected.value = hasInternet
                        updateConnectionType(networkCapabilities)
                        if (hasInternet) {
                            NetworkRetryPolicy.triggerImmediateRetry()
                        }
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register network callback: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "NetworkMonitor"

        @Volatile
        private var instance: NetworkConnectivityMonitor? = null

        fun getInstance(context: Context): NetworkConnectivityMonitor {
            return instance ?: synchronized(this) {
                instance ?: NetworkConnectivityMonitor(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
}
