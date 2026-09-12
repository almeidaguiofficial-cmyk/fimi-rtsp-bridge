package com.b2tech.fimirtsp.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

data class Connectivity(
    val hasInternet: Boolean,
    val transport: Transport,
) {
    enum class Transport { WIFI, CELLULAR, ETHERNET, USB_OR_OTHER, NONE }
}

/**
 * Observa a rede padrão do sistema. NÃO faz bind de socket em rede específica —
 * queremos que a lib RTSP viaje pelo caminho padrão (celular na maioria dos casos),
 * enquanto o FIMI Navi conversa pelo USB do controle sem interferência.
 */
class ConnectivityMonitor(private val context: Context) {

    private val cm: ConnectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    fun snapshot(): Connectivity {
        val network = cm.activeNetwork ?: return Connectivity(false, Connectivity.Transport.NONE)
        val caps = cm.getNetworkCapabilities(network) ?: return Connectivity(false, Connectivity.Transport.NONE)
        return Connectivity(
            hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
            transport = transportOf(caps),
        )
    }

    fun observe(): Flow<Connectivity> = callbackFlow {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(snapshot()) }
            override fun onLost(network: Network) { trySend(snapshot()) }
            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                trySend(snapshot())
            }
        }
        cm.registerNetworkCallback(request, callback)
        trySend(snapshot())
        awaitClose { cm.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    private fun transportOf(caps: NetworkCapabilities): Connectivity.Transport = when {
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Connectivity.Transport.WIFI
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Connectivity.Transport.CELLULAR
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> Connectivity.Transport.ETHERNET
        else -> Connectivity.Transport.USB_OR_OTHER
    }
}
