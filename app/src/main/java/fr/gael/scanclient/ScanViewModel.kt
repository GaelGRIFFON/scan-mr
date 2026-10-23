package fr.gael.scanclient

import android.app.Application
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface Screen {
    data object Home : Screen
    data object Scan : Screen
    data object Detail : Screen
    data object Stores : Screen
}

sealed interface Status {
    data object Idle : Status
    data class Saved(val code: String) : Status
    data class Duplicate(val code: String) : Status
    data class UnknownStore(val prefix: String) : Status
    data class Uncertain(val stores: List<String>) : Status
}

@OptIn(ExperimentalCoroutinesApi::class)
class ScanViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDb.get(app).dao()
    private val whileSubscribed = SharingStarted.WhileSubscribed(5_000)

    val screen = MutableStateFlow<Screen>(Screen.Home)
    /** Session affichée : en cours (écran Scan) ou consultée (écran Détail). */
    val currentSessionId = MutableStateFlow<Long?>(null)

    val sessions: StateFlow<List<SessionRow>> =
        dao.observeSessions().stateIn(viewModelScope, whileSubscribed, emptyList())

    val stores: StateFlow<List<StoreEntity>> =
        dao.observeStores().stateIn(viewModelScope, whileSubscribed, emptyList())

    val scans: StateFlow<List<ScanEntity>> = currentSessionId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList<ScanEntity>()) else dao.observeScans(id) }
        .stateIn(viewModelScope, whileSubscribed, emptyList())

    val recap: StateFlow<List<StoreCount>> = currentSessionId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList<StoreCount>()) else dao.observeRecap(id) }
        .stateIn(viewModelScope, whileSubscribed, emptyList())

    val currentSession: StateFlow<SessionEntity?> = currentSessionId
        .flatMapLatest { id -> if (id == null) flowOf<SessionEntity?>(null) else dao.observeSession(id) }
        .stateIn(viewModelScope, whileSubscribed, null)

    private val _status = MutableStateFlow<Status>(Status.Idle)
    val status = _status.asStateFlow()

    /** Magasins inconnus vus pendant le scan, en attente d'être ajoutés (ou ignorés). */
    private val _tray = MutableStateFlow<List<Reading.Unknown>>(emptyList())
    val unknownTray = _tray.asStateFlow()

    @Volatile private var storeDefs: List<StoreDef> = emptyList()
    private var lastKey: String? = null
    private var lastKeyAt = 0L
    private val recent = HashMap<String, Pair<String, Long>>()
    private var clearJob: Job? = null

    private val tone by lazy { ToneGenerator(AudioManager.STREAM_MUSIC, 80) }
    private val vibrator by lazy { app.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator }

    init {
        viewModelScope.launch {
            dao.observeStores().collect { list ->
                val defs = list.map { StoreDef(it.prefix, it.clientLen) }
                storeDefs = defs
                // Un inconnu devenu reconnu quitte la liste d'attente
                _tray.value = _tray.value.filter { CodeParser.read(it.prefix + it.client, defs) !is Reading.Match }
            }
        }
    }

    fun storeDefs(): List<StoreDef> = storeDefs

    // ---------------------------------------------------------------- navigation

    fun goHome() {
        screen.value = Screen.Home
    }

    fun openStores() {
        screen.value = Screen.Stores
    }

    // ---------------------------------------------------------------- sessions

    fun newSession() = viewModelScope.launch {
        val now = System.currentTimeMillis()
        dao.endOpenSessions(now)
        val name = "Session du " + SimpleDateFormat("dd/MM 'à' HH:mm", Locale.FRANCE).format(Date(now))
        val id = dao.insertSession(SessionEntity(name = name, createdAt = now))
        startScanning(id)
    }

    /** Reprend le scan d'une session (en cours, ou terminée que l'on rouvre). */
    fun resumeSession(id: Long) = viewModelScope.launch {
        dao.endOpenSessions(System.currentTimeMillis())
        dao.reopenSession(id)
        startScanning(id)
    }

    fun viewSession(id: Long) {
        currentSessionId.value = id
        screen.value = Screen.Detail
    }

    fun endSession() = viewModelScope.launch {
        currentSessionId.value?.let { dao.endSession(it, System.currentTimeMillis()) }
        screen.value = Screen.Home
    }

    fun renameSession(name: String) = viewModelScope.launch {
        val id = currentSessionId.value ?: return@launch
        val n = name.trim()
        if (n.isNotEmpty()) dao.renameSession(id, n)
    }

    fun deleteCurrentSession() = viewModelScope.launch {
        val id = currentSessionId.value ?: return@launch
        dao.deleteScansOf(id)
        dao.deleteSession(id)
        currentSessionId.value = null
        screen.value = Screen.Home
    }

    private fun startScanning(id: Long) {
        lastKey = null
        recent.clear()
        _tray.value = emptyList()
        _status.value = Status.Idle
        currentSessionId.value = id
        screen.value = Screen.Scan
    }

    suspend fun exportIntent(): Intent? {
        val id = currentSessionId.value ?: return null
        val session = dao.getSession(id) ?: return null
        return CsvExport.chooserIntent(getApplication(), session, dao.scansOf(id))
    }

    // ---------------------------------------------------------------- scan

    fun onMatch(m: Reading.Match) {
        if (screen.value != Screen.Scan) return
        val now = SystemClock.elapsedRealtime()
        val full = m.full
        // Même code toujours devant la caméra : silence tant qu'il n'a pas disparu pendant plus de 4 s
        if (full == lastKey && now - lastKeyAt < 4_000) {
            lastKeyAt = now
            return
        }
        // Même n° client lu avec un autre magasin dans les 10 s : même document mal lu, on ignore
        val prev = recent[m.client]
        if (prev != null && prev.first != m.store && now - prev.second < 10_000) return
        if (recent.size > 200) recent.clear()
        recent[m.client] = m.store to now
        lastKey = full
        lastKeyAt = now
        save(m.store, m.client)
    }

    fun onUnknown(u: Reading.Unknown) {
        if (screen.value != Screen.Scan) return
        _tray.value = (listOf(u) + _tray.value.filter { it.prefix != u.prefix }).take(3)
        show(Status.UnknownStore(u.prefix), 3_000)
    }

    fun onAmbiguous(a: Reading.Ambiguous) {
        if (screen.value != Screen.Scan) return
        show(Status.Uncertain(a.stores), 3_000)
    }

    fun dismissUnknown(prefix: String) {
        _tray.value = _tray.value.filter { it.prefix != prefix }
    }

    private fun save(store: String, client: String) {
        val sid = currentSessionId.value ?: return
        viewModelScope.launch {
            val full = store + client
            val id = dao.insertScan(
                ScanEntity(sessionId = sid, store = store, client = client, full = full, ts = System.currentTimeMillis()),
            )
            if (id == -1L) {
                feedback(ok = false)
                show(Status.Duplicate(full))
            } else {
                feedback(ok = true)
                show(Status.Saved(full))
            }
        }
    }

    fun deleteScan(e: ScanEntity) = viewModelScope.launch { dao.deleteScan(e.id) }

    fun restoreScan(e: ScanEntity) = viewModelScope.launch { dao.insertScan(e) }

    fun updateScan(e: ScanEntity, store: String, client: String) = viewModelScope.launch {
        val s = CodeParser.normalize(store)
        val c = CodeParser.normalize(client)
        if (s.isEmpty() || c.isEmpty()) return@launch
        if (dao.updateScan(e.copy(store = s, client = c, full = s + c)) == 0) {
            feedback(ok = false)
            show(Status.Duplicate(s + c))
        }
    }

    // ---------------------------------------------------------------- magasins

    /**
     * Ajoute (ou remplace) un préfixe. Si [pendingClient] est fourni (ajout depuis un "magasin inconnu"
     * vu pendant le scan), le scan en attente est enregistré tout de suite.
     */
    fun addStore(prefixRaw: String, len: Int?, pendingClient: String? = null) {
        val p = CodeParser.normalize(prefixRaw)
        if (p.length < 2) return
        viewModelScope.launch {
            dao.upsertStore(StoreEntity(p, len))
            if (pendingClient != null) {
                val lens = len?.let { listOf(it) } ?: CodeParser.DEFAULT_CLIENT_LENS
                val c = CodeParser.normalize(pendingClient)
                if (c.length in lens && c.all { it.isDigit() }) save(p, c)
            }
        }
    }

    fun updateStore(oldPrefix: String, newPrefixRaw: String, len: Int?) = viewModelScope.launch {
        val p = CodeParser.normalize(newPrefixRaw)
        if (p.length < 2) return@launch
        if (p != oldPrefix) dao.deleteStore(oldPrefix)
        dao.upsertStore(StoreEntity(p, len))
    }

    fun deleteStore(prefix: String) = viewModelScope.launch { dao.deleteStore(prefix) }

    /** Colle une liste : une ligne par magasin, "PREFIXE" ou "PREFIXE;chiffres" (séparateurs ; , tab ou espace). */
    fun importStores(text: String) = viewModelScope.launch {
        for (line in text.lines()) {
            val parts = line.split(';', ',', '\t', ' ').filter { it.isNotBlank() }
            val p = parts.firstOrNull()?.let { CodeParser.normalize(it) } ?: continue
            if (p.length < 2) continue
            dao.upsertStore(StoreEntity(p, parts.getOrNull(1)?.trim()?.toIntOrNull()))
        }
    }

    // ---------------------------------------------------------------- retours utilisateur

    private fun show(s: Status, ms: Long = 2_500) {
        _status.value = s
        clearJob?.cancel()
        clearJob = viewModelScope.launch {
            delay(ms)
            _status.value = Status.Idle
        }
    }

    private fun feedback(ok: Boolean) {
        tone.startTone(if (ok) ToneGenerator.TONE_PROP_ACK else ToneGenerator.TONE_PROP_NACK, 150)
        vibrator.vibrate(VibrationEffect.createOneShot(if (ok) 60 else 200, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    override fun onCleared() {
        tone.release()
    }
}
