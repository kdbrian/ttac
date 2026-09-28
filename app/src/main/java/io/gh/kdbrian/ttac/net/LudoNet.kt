package io.gh.kdbrian.ttac.net

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import io.gh.kdbrian.ttac.game.LudoAction
import io.gh.kdbrian.ttac.game.LudoSeat
import io.gh.kdbrian.ttac.game.LudoState
import io.gh.kdbrian.ttac.net.LanSession.Companion.readBoundedLine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

const val LUDO_SERVICE_TYPE = "_ttacludo._tcp."
const val LUDO_PROTOCOL = 1

/**
 * Messages for multi-phone Ludo. The host is the single source of truth: clients send [Act] intents, the host
 * validates them with the same reducer the game uses and broadcasts the resulting [State] to everyone.
 */
@Serializable
sealed interface LudoWire {
    @Serializable @SerialName("join")
    data class Join(val name: String, val alias: String, val color: Long, val version: Int = LUDO_PROTOCOL) : LudoWire

    /** Host → one client: your device number (1..3). */
    @Serializable @SerialName("welcome")
    data class Welcome(val device: Int) : LudoWire

    /** Host → everyone: the seating plan before the game starts. */
    @Serializable @SerialName("lobby")
    data class Lobby(val seats: List<LudoSeat>, val hostAlias: String) : LudoWire

    @Serializable @SerialName("state")
    data class State(val state: LudoState) : LudoWire

    @Serializable @SerialName("act")
    data class Act(val action: LudoAction) : LudoWire

    @Serializable @SerialName("full")
    data object Full : LudoWire

    @Serializable @SerialName("bye")
    data object Bye : LudoWire
}

object LudoCodec {
    private val json = Json { ignoreUnknownKeys = true; classDiscriminator = "t"; encodeDefaults = true }
    fun encode(m: LudoWire): String = json.encodeToString(LudoWire.serializer(), m)
    fun decode(line: String): LudoWire? = runCatching { json.decodeFromString(LudoWire.serializer(), line) }.getOrNull()
}

/** A discovered Ludo table. */
data class LudoTable(val key: String, val alias: String, val hostName: String, internal val address: String, internal val port: Int)

/** Everything a joining phone learns from the host. */
data class LudoGuest(val device: Int, val name: String, val alias: String, val color: Long)

sealed interface LudoLinkStatus {
    data object Idle : LudoLinkStatus
    data class Hosting(val alias: String, val code: String?, val guests: List<LudoGuest>) : LudoLinkStatus
    data class Joining(val alias: String) : LudoLinkStatus
    data class Joined(val device: Int, val hostAlias: String) : LudoLinkStatus
    data class Failed(val reason: String) : LudoLinkStatus
}

/**
 * Network link for Ludo: host mode accepts up to three phones; guest mode connects to one host.
 * Incoming messages arrive on [inbox] tagged with the sending device (0 = host).
 */
class LudoLink(context: Context) {
    private val nsd = context.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var server: ServerSocket? = null
    private val peers = ConcurrentHashMap<Int, Socket>()
    private val outs = ConcurrentHashMap<Int, OutputStream>()
    private var registration: NsdManager.RegistrationListener? = null
    private var discovery: NsdManager.DiscoveryListener? = null
    private var multicast: WifiManager.MulticastLock? = null

    private val _status = MutableStateFlow<LudoLinkStatus>(LudoLinkStatus.Idle)
    val status: StateFlow<LudoLinkStatus> = _status.asStateFlow()

    private val _tables = MutableStateFlow<List<LudoTable>>(emptyList())
    val tables: StateFlow<List<LudoTable>> = _tables.asStateFlow()

    private val _inbox = MutableSharedFlow<Pair<Int, LudoWire>>(extraBufferCapacity = 128)
    val inbox: SharedFlow<Pair<Int, LudoWire>> = _inbox.asSharedFlow()

    // ---- Host -------------------------------------------------------------------------------

    fun host(alias: String, myName: String, maxGuests: Int) {
        close()
        val srv = ServerSocket(0).also { server = it }
        val code = LanSession.localIpAddress()?.let { SessionCode.encode(it, srv.localPort) }
        _status.value = LudoLinkStatus.Hosting(alias, code, emptyList())
        register(alias, myName, srv.localPort)
        scope.launch {
            while (!srv.isClosed) {
                val s = runCatching { srv.accept() }.getOrNull() ?: break
                val guests = (_status.value as? LudoLinkStatus.Hosting)?.guests.orEmpty()
                val device = (1..3).firstOrNull { d -> guests.none { it.device == d } && !peers.containsKey(d) }
                if (device == null || guests.size >= maxGuests) {
                    runCatching { s.getOutputStream().write((LudoCodec.encode(LudoWire.Full) + "\n").toByteArray()); s.close() }
                    continue
                }
                s.tcpNoDelay = true
                peers[device] = s
                outs[device] = s.getOutputStream()
                read(device, s)
            }
        }
    }

    private fun register(alias: String, myName: String, port: Int) {
        val info = NsdServiceInfo().apply {
            serviceName = "Ludo|$alias|${myName.take(16)}".take(63)
            serviceType = LUDO_SERVICE_TYPE
            setPort(port)
        }
        val l = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(i: NsdServiceInfo) {}
            override fun onRegistrationFailed(i: NsdServiceInfo, e: Int) {}
            override fun onServiceUnregistered(i: NsdServiceInfo) {}
            override fun onUnregistrationFailed(i: NsdServiceInfo, e: Int) {}
        }
        registration = l
        runCatching { nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, l) }
    }

    /** Host: stop advertising once the game starts (seats are fixed). */
    fun stopAdvertising() {
        registration?.let { runCatching { nsd.unregisterService(it) } }
        registration = null
    }

    fun sendTo(device: Int, m: LudoWire) {
        val out = outs[device] ?: return
        scope.launch { runCatching { synchronized(out) { out.write((LudoCodec.encode(m) + "\n").toByteArray()); out.flush() } } }
    }

    fun broadcast(m: LudoWire) = outs.keys.forEach { sendTo(it, m) }

    // ---- Guest ------------------------------------------------------------------------------

    fun discover() {
        stopDiscovery()
        _tables.value = emptyList()
        multicast = wifi.createMulticastLock("ttac-ludo").apply { setReferenceCounted(false); acquire() }
        val l = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(t: String) {}
            override fun onDiscoveryStopped(t: String) {}
            override fun onStartDiscoveryFailed(t: String, e: Int) {}
            override fun onStopDiscoveryFailed(t: String, e: Int) {}
            override fun onServiceFound(info: NsdServiceInfo) {
                @Suppress("DEPRECATION")
                runCatching {
                    nsd.resolveService(info, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(i: NsdServiceInfo, e: Int) {}
                        override fun onServiceResolved(r: NsdServiceInfo) {
                            val addr = r.host?.hostAddress ?: return
                            val parts = info.serviceName.split("|")
                            val alias = parts.getOrNull(1) ?: "Ludo table"
                            val host = parts.drop(2).joinToString("|")
                            _tables.update { list -> list.filterNot { it.key == info.serviceName } + LudoTable(info.serviceName, alias, host, addr, r.port) }
                        }
                    })
                }
            }
            override fun onServiceLost(info: NsdServiceInfo) {
                _tables.update { list -> list.filterNot { it.key == info.serviceName } }
            }
        }
        discovery = l
        runCatching { nsd.discoverServices(LUDO_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, l) }
    }

    fun stopDiscovery() {
        discovery?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        discovery = null
        multicast?.let { runCatching { it.release() } }
        multicast = null
    }

    fun join(address: String, port: Int, alias: String, hello: LudoWire.Join) {
        stopDiscovery()
        _status.value = LudoLinkStatus.Joining(alias)
        scope.launch {
            val s = Socket()
            if (runCatching { s.connect(InetSocketAddress(address, port), 6_000) }.isFailure) {
                runCatching { s.close() }
                _status.value = LudoLinkStatus.Failed("Couldn't reach $alias")
                return@launch
            }
            s.tcpNoDelay = true
            peers[0] = s
            outs[0] = s.getOutputStream()
            sendTo(0, hello)
            read(0, s)
        }
    }

    private fun read(device: Int, s: Socket) {
        scope.launch {
            val input = s.getInputStream().buffered()
            runCatching {
                while (true) {
                    val line = input.readBoundedLine() ?: break
                    val m = LudoCodec.decode(line) ?: continue
                    when {
                        m is LudoWire.Welcome -> _status.value = LudoLinkStatus.Joined(m.device, (_status.value as? LudoLinkStatus.Joining)?.alias ?: "")
                        m is LudoWire.Full -> { _status.value = LudoLinkStatus.Failed("That table is full"); break }
                        m is LudoWire.Join && device != 0 -> {
                            val guest = LudoGuest(device, m.name.take(16), m.alias.take(40), m.color)
                            _status.update { st -> if (st is LudoLinkStatus.Hosting) st.copy(guests = st.guests.filterNot { it.device == device } + guest) else st }
                            sendTo(device, LudoWire.Welcome(device))
                        }
                    }
                    _inbox.emit(device to m)
                    if (m is LudoWire.Bye) break
                }
            }
            peers.remove(device); outs.remove(device)
            runCatching { s.close() }
            _status.update { st -> if (st is LudoLinkStatus.Hosting) st.copy(guests = st.guests.filterNot { it.device == device }) else st }
            _inbox.emit(device to LudoWire.Bye)
            if (device == 0) _status.value = LudoLinkStatus.Failed("Lost the host")
        }
    }

    fun close() {
        runCatching { broadcast(LudoWire.Bye) }
        stopDiscovery()
        stopAdvertising()
        runCatching { server?.close() }
        server = null
        peers.values.forEach { runCatching { it.close() } }
        peers.clear(); outs.clear()
        scope.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        _status.value = LudoLinkStatus.Idle
        _tables.value = emptyList()
    }
}
