package io.gh.kdbrian.ttac.net

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import kotlin.coroutines.resume

/** A discovered session. [address] is kept internal — the UI only shows [alias] and [hostName]. */
data class LanHost(val key: String, val alias: String, val hostName: String, internal val address: String, internal val port: Int)

sealed interface LanStatus {
    data object Idle : LanStatus
    /** [code] lets someone join by hand if discovery is blocked; null when no LAN address was found. */
    data class Hosting(val alias: String, val code: String?) : LanStatus
    data object Discovering : LanStatus
    data class Connecting(val alias: String) : LanStatus
    data class Connected(val remoteName: String, val remoteColor: Long, val boardSize: Int, val isHost: Boolean, val remoteAlias: String) : LanStatus
    data class Failed(val reason: String) : LanStatus
    data class Disconnected(val reason: String) : LanStatus
}

/**
 * One peer-to-peer connection over the local network. The host opens a TCP server socket and
 * advertises it with NSD (mDNS); the joiner discovers it (or types the IP) and connects.
 * No data leaves the LAN and there is no server.
 */
class LanSession(context: Context) {

    private val appContext = context.applicationContext
    private val nsd = appContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifi = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private var scope = newScope()
    private var server: ServerSocket? = null
    private var socket: Socket? = null
    private var output: OutputStream? = null
    private var registration: NsdManager.RegistrationListener? = null
    private var discovery: NsdManager.DiscoveryListener? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var readerJob: Job? = null
    private val writeLock = Any()

    private val _status = MutableStateFlow<LanStatus>(LanStatus.Idle)
    val status: StateFlow<LanStatus> = _status.asStateFlow()

    private val _hosts = MutableStateFlow<List<LanHost>>(emptyList())
    val hosts: StateFlow<List<LanHost>> = _hosts.asStateFlow()

    private val _messages = MutableSharedFlow<LanMessage>(extraBufferCapacity = 64)
    val messages: SharedFlow<LanMessage> = _messages.asSharedFlow()

    private fun newScope() = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ---- Hosting --------------------------------------------------------------------------

    /** Hosts under this device's persistent [alias], so opponents keep a history with it. */
    fun host(alias: String, myName: String, myColor: Long, boardSize: Int) {
        close(sendBye = false)
        val server = ServerSocket(0).also { this.server = it }
        _status.value = LanStatus.Hosting(alias, localIpAddress()?.let { SessionCode.encode(it, server.localPort) })
        register(alias, myName, server.localPort)
        scope.launch {
            val client = runCatching { server.accept() }.getOrNull() ?: return@launch
            unregister()
            runCatching { server.close() }
            attach(client)
            send(LanMessage.Hello(myName, myColor, boardSize, alias = alias))
            awaitHello(isHost = true, hostBoardSize = boardSize)
        }
    }

    private fun register(alias: String, myName: String, port: Int) {
        val info = NsdServiceInfo().apply {
            serviceName = "TTac|$alias|${myName.take(16)}".take(63)
            serviceType = SERVICE_TYPE
            setPort(port)
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {}
            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {}
            override fun onServiceUnregistered(info: NsdServiceInfo) {}
            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {}
        }
        registration = listener
        runCatching { nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener) }
    }

    private fun unregister() {
        registration?.let { runCatching { nsd.unregisterService(it) } }
        registration = null
    }

    // ---- Joining --------------------------------------------------------------------------

    fun startDiscovery() {
        stopDiscovery()
        _hosts.value = emptyList()
        _status.value = LanStatus.Discovering
        multicastLock = wifi.createMulticastLock("ttac-nsd").apply { setReferenceCounted(false); acquire() }

        // NsdManager can only resolve one service at a time on older releases, so queue them.
        val resolveQueue = Channel<NsdServiceInfo>(Channel.UNLIMITED)
        scope.launch {
            for (found in resolveQueue) {
                val resolved = withTimeoutOrNull(5_000) { resolve(found) } ?: continue
                val address = resolved.hostAddressCompat() ?: continue
                val (alias, hostName) = parseServiceName(found.serviceName)
                _hosts.update { list -> (list.filterNot { it.key == found.serviceName } + LanHost(found.serviceName, alias, hostName, address, resolved.port)) }
            }
        }

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                _status.value = LanStatus.Failed("Discovery failed ($errorCode). Try joining by IP.")
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onServiceFound(info: NsdServiceInfo) {
                if (info.serviceType.contains("_ttac")) resolveQueue.trySend(info)
            }
            override fun onServiceLost(info: NsdServiceInfo) {
                _hosts.update { list -> list.filterNot { it.key == info.serviceName } }
            }
        }
        discovery = listener
        runCatching { nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener) }
            .onFailure { _status.value = LanStatus.Failed("Discovery unavailable. Try joining by IP.") }
    }

    @Suppress("DEPRECATION") // resolveService is still the simplest path on all supported API levels.
    private suspend fun resolve(info: NsdServiceInfo): NsdServiceInfo? = suspendCancellableCoroutine { cont ->
        nsd.resolveService(info, object : NsdManager.ResolveListener {
            override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                if (cont.isActive) cont.resume(null)
            }
            override fun onServiceResolved(info: NsdServiceInfo) {
                if (cont.isActive) cont.resume(info)
            }
        })
    }

    @Suppress("DEPRECATION")
    private fun NsdServiceInfo.hostAddressCompat(): String? =
        if (Build.VERSION.SDK_INT >= 34) hostAddresses.firstOrNull { it is Inet4Address }?.hostAddress ?: hostAddresses.firstOrNull()?.hostAddress
        else host?.hostAddress

    fun stopDiscovery() {
        discovery?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        discovery = null
        multicastLock?.let { runCatching { it.release() } }
        multicastLock = null
    }

    fun join(address: String, port: Int, alias: String, myAlias: String, myName: String, myColor: Long) {
        stopDiscovery()
        _status.value = LanStatus.Connecting(alias)
        scope.launch {
            val s = Socket()
            val ok = runCatching { s.connect(InetSocketAddress(address, port), 6_000) }.isSuccess
            if (!ok) {
                runCatching { s.close() }
                _status.value = LanStatus.Failed("Couldn't reach $alias. Is it still hosting?")
                return@launch
            }
            attach(s)
            send(LanMessage.Hello(myName, myColor, 0, alias = myAlias))
            awaitHello(isHost = false, hostBoardSize = null)
        }
    }

    // ---- Connection -----------------------------------------------------------------------

    private fun attach(s: Socket) {
        s.tcpNoDelay = true
        socket = s
        output = s.getOutputStream()
    }

    /** Reads until the peer's Hello arrives, then marks the session connected and relays the rest. */
    private fun awaitHello(isHost: Boolean, hostBoardSize: Int?) {
        val input = socket?.getInputStream()?.buffered() ?: return
        readerJob = scope.launch {
            var connected = false
            val reason = runCatching {
                while (true) {
                    val line = input.readBoundedLine() ?: break
                    val message = Protocol.decode(line) ?: continue
                    if (!connected) {
                        if (message is LanMessage.Hello) {
                            if (message.version != PROTOCOL_VERSION) {
                                _status.value = LanStatus.Failed("Opponent has a different app version")
                                break
                            }
                            val size = hostBoardSize ?: message.boardSize
                            if (size !in io.gh.kdbrian.ttac.game.Rules.SUPPORTED_SIZES) break
                            connected = true
                            _status.value = LanStatus.Connected(message.name.take(16), message.color, size, isHost, message.alias.take(40))
                        }
                        continue
                    }
                    if (message is LanMessage.Bye) {
                        _status.value = LanStatus.Disconnected("Opponent left the game")
                        break
                    }
                    _messages.emit(message)
                }
                "Connection lost"
            }.getOrElse { "Connection lost" }
            if (_status.value is LanStatus.Connected) _status.value = LanStatus.Disconnected(reason)
            closeSocket()
        }
    }

    fun send(message: LanMessage) {
        val out = output ?: return
        scope.launch {
            runCatching {
                synchronized(writeLock) {
                    out.write((Protocol.encode(message) + "\n").toByteArray(Charsets.UTF_8))
                    out.flush()
                }
            }
        }
    }

    fun close(sendBye: Boolean = true) {
        val out = output
        if (sendBye && out != null && _status.value is LanStatus.Connected) {
            // Sockets can't be written on the main thread; give the goodbye a brief moment to go out.
            Thread {
                runCatching {
                    synchronized(writeLock) {
                        out.write((Protocol.encode(LanMessage.Bye) + "\n").toByteArray(Charsets.UTF_8))
                        out.flush()
                    }
                }
            }.apply { start(); join(300) }
        }
        stopDiscovery()
        unregister()
        runCatching { server?.close() }
        server = null
        closeSocket()
        scope.cancel()
        scope = newScope()
        _hosts.value = emptyList()
        _status.value = LanStatus.Idle
    }

    private fun closeSocket() {
        runCatching { socket?.close() }
        socket = null
        output = null
    }

    companion object {
        private const val MAX_LINE = 2048

        /** "TTac|Cosmic Otter 42|Ann" → ("Cosmic Otter 42", "Ann"). */
        internal fun parseServiceName(name: String): Pair<String, String> {
            val parts = name.split("|")
            return if (parts.size >= 3 && parts[0] == "TTac") parts[1] to parts.drop(2).joinToString("|")
            else "Mystery Session" to name.removePrefix("TTac").trim()
        }

        /** Like readLine(), but refuses absurdly long lines so a misbehaving peer can't exhaust memory. */
        internal fun InputStream.readBoundedLine(): String? {
            val bytes = java.io.ByteArrayOutputStream()
            while (true) {
                val b = read()
                if (b == -1) return if (bytes.size() == 0) null else bytes.toString(Charsets.UTF_8.name())
                if (b == '\n'.code) return bytes.toString(Charsets.UTF_8.name())
                if (bytes.size() >= MAX_LINE) throw IllegalStateException("Line too long")
                bytes.write(b)
            }
        }

        fun localIpAddress(): String? = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
                ?.hostAddress
        }.getOrNull()
    }
}
