package fr.gael.scanclient

import android.app.Application
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface Status {
    data object Idle : Status
    data class Saved(val code: String, val newStore: Boolean) : Status
    data class Duplicate(val code: String) : Status
}

class ScanViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDb.get(app).dao()

    val scans: StateFlow<List<ScanEntity>> =
        dao.observeScans().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _status = MutableStateFlow<Status>(Status.Idle)
    val status = _status.asStateFlow()

    @Volatile private var known: Set<String> = emptySet()
    private var lastKey: String? = null
    private var lastKeyAt = 0L
    private var clearJob: Job? = null

    private val tone by lazy { ToneGenerator(AudioManager.STREAM_MUSIC, 80) }
    private val vibrator by lazy { app.getSystemService(Application.VIBRATOR_SERVICE) as Vibrator }

    init {
        viewModelScope.launch { dao.observeStores().collect { known = it.toSet() } }
    }

    fun knownStores(): Set<String> = known

    /** Appelé par l'analyseur quand un code est lu de façon stable. */
    fun onCandidate(p: ParsedCode) {
        val now = SystemClock.elapsedRealtime()
        // Même code toujours devant la caméra : on reste silencieux tant qu'il n'a pas disparu > 4 s
        if (p.full == lastKey && now - lastKeyAt < 4_000) {
            lastKeyAt = now
            return
        }
        lastKey = p.full
        lastKeyAt = now
        viewModelScope.launch {
            val id = dao.insert(ScanEntity(store = p.store, client = p.client, full = p.full, ts = System.currentTimeMillis()))
            if (id == -1L) {
                feedback(ok = false)
                show(Status.Duplicate(p.full))
            } else {
                feedback(ok = true)
                show(Status.Saved(p.full, newStore = p.store !in known))
            }
        }
    }

    fun delete(e: ScanEntity) = viewModelScope.launch { dao.delete(e.id) }

    fun restore(e: ScanEntity) = viewModelScope.launch { dao.insert(e) }

    fun clearAll() = viewModelScope.launch { dao.clear() }

    fun update(e: ScanEntity, store: String, client: String) = viewModelScope.launch {
        val s = CodeParser.normalize(store)
        val c = CodeParser.normalize(client)
        if (s.isEmpty() || c.isEmpty()) return@launch
        if (dao.update(e.copy(store = s, client = c, full = s + c)) == 0) {
            feedback(ok = false)
            show(Status.Duplicate(s + c))
        }
    }

    suspend fun exportIntent(): Intent = CsvExport.chooserIntent(getApplication(), dao.all())

    private fun show(s: Status) {
        _status.value = s
        clearJob?.cancel()
        clearJob = viewModelScope.launch {
            delay(2_500)
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
