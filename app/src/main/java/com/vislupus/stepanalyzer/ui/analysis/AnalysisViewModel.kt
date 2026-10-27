package com.vislupus.stepanalyzer.ui.analysis

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vislupus.stepanalyzer.AppContainer
import com.vislupus.stepanalyzer.config.AppConfig
import com.vislupus.stepanalyzer.core.algorithms.Algorithms
import com.vislupus.stepanalyzer.core.analysis.AnalysisParams
import com.vislupus.stepanalyzer.core.analysis.Analyzer
import com.vislupus.stepanalyzer.core.analysis.Preprocessor
import com.vislupus.stepanalyzer.core.csv.CsvParser
import com.vislupus.stepanalyzer.core.gpx.Gpx
import com.vislupus.stepanalyzer.core.gpx.GpxTrack
import com.vislupus.stepanalyzer.core.model.AnalysisResult
import com.vislupus.stepanalyzer.core.model.GpxSummary
import com.vislupus.stepanalyzer.core.model.PreprocessOptions
import com.vislupus.stepanalyzer.core.model.Preprocessed
import com.vislupus.stepanalyzer.core.model.RawData
import com.vislupus.stepanalyzer.data.db.Session
import com.vislupus.stepanalyzer.util.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Loads a session's files, runs the analysis and re-runs it whenever the user tweaks a
 * parameter. Preprocessing (CSV parse + resample + filter) is cached and only repeated when
 * the preprocessing options change; algorithm re-runs are fast enough for live sliders.
 */
class AnalysisViewModel(private val container: AppContainer, private val sessionId: Long) : ViewModel() {

    sealed class UiState {
        data class Loading(val stage: Int) : UiState()
        data class Error(val message: String) : UiState()
        data class Ready(val data: Data) : UiState()
    }

    data class Data(
        val session: Session,
        val pre: Preprocessed,
        val gpx: GpxSummary?,
        val result: AnalysisResult,
        /** Current parameter overrides per algorithm. */
        val params: Map<String, Map<String, Double>>,
        val options: PreprocessOptions,
        val referenceId: String?,
        val disabled: Set<String>,
        val dirty: Boolean,
        val savedRunId: Long?,
        val busy: Boolean = false,
    )

    private val _state = MutableStateFlow<UiState>(UiState.Loading(0))
    val state: StateFlow<UiState> get() = _state

    private var raw: RawData? = null
    private var gpxTrack: GpxTrack? = null
    private var rerunJob: Job? = null

    init {
        load()
    }

    private fun settings() = container.settings.current

    fun load() {
        viewModelScope.launch {
            _state.value = UiState.Loading(0)
            try {
                val session = withContext(Dispatchers.IO) { container.sessions.get(sessionId) }
                    ?: throw IllegalStateException("Session $sessionId not found")
                val csv = session.csvPath?.let { File(it) }
                if (csv == null || !csv.exists()) throw IllegalStateException("The sensor file of this recording is missing")
                _state.value = UiState.Loading(1)
                val rawData = withContext(Dispatchers.IO) { csv.bufferedReader().use { CsvParser.parse(it) } }
                raw = rawData
                _state.value = UiState.Loading(2)
                val track = withContext(Dispatchers.IO) {
                    session.gpxPath?.let { File(it) }?.takeIf { it.exists() }?.let { f ->
                        try { f.inputStream().use { Gpx.parse(it) } } catch (e: Exception) { null }
                    }
                }
                gpxTrack = track
                val s = settings()
                val options = s.preprocessOptions()
                val params = s.algorithmParams
                val disabled = s.disabledAlgorithms
                val (pre, result, gpx) = withContext(Dispatchers.Default) { compute(rawData, track, options, params, disabled, null) }
                // store the first analysis of a recording automatically; later runs are saved
                // explicitly with the save button so parameter experiments do not pile up
                var savedRunId: Long? = null
                if (AppConfig.Analysis.AUTO_SAVE_RUNS) {
                    savedRunId = withContext(Dispatchers.IO) {
                        if (container.sessions.runs(sessionId).isEmpty()) container.sessions.saveRun(session, result, params, options) else null
                    }
                }
                _state.value = UiState.Ready(
                    Data(
                        session = container.sessions.get(sessionId) ?: session,
                        pre = pre, gpx = gpx, result = result, params = params, options = options,
                        referenceId = null, disabled = disabled, dirty = false, savedRunId = savedRunId,
                    )
                )
            } catch (e: Exception) {
                _state.value = UiState.Error(e.message ?: e.toString())
            }
        }
    }

    private fun compute(
        rawData: RawData, track: GpxTrack?, options: PreprocessOptions,
        params: Map<String, Map<String, Double>>, disabled: Set<String>, referenceId: String?,
        preCached: Preprocessed? = null,
    ): Triple<Preprocessed, AnalysisResult, GpxSummary?> {
        val pre = preCached ?: Preprocessor.preprocess(rawData, options)
        val gpx = track?.let { Gpx.summarise(it, settings().maxGpsSpeedMps) }
        val s = settings()
        val result = Analyzer.analyze(
            pre, gpx,
            AnalysisParams(
                algorithmParams = params,
                enabledAlgorithms = Algorithms.all.map { it.id }.filter { it !in disabled }.toSet(),
                referenceAlgorithmId = referenceId,
                heightM = s.heightM,
                sex = s.sex,
            ),
        )
        return Triple(pre, result, gpx)
    }

    private fun current(): Data? = (_state.value as? UiState.Ready)?.data

    /** Re-run the algorithms (debounced) with the current parameters. */
    private fun rerun(newParams: Map<String, Map<String, Double>>? = null, newRef: String? = null, refChanged: Boolean = false, newDisabled: Set<String>? = null, immediate: Boolean = false) {
        val data = current() ?: return
        val params = newParams ?: data.params
        val ref = if (refChanged) newRef else data.referenceId
        val disabled = newDisabled ?: data.disabled
        _state.value = UiState.Ready(data.copy(params = params, referenceId = ref, disabled = disabled, dirty = true))
        rerunJob?.cancel()
        rerunJob = viewModelScope.launch {
            if (!immediate) delay(AppConfig.Analysis.PARAM_DEBOUNCE_MS)
            val rawData = raw ?: return@launch
            val (_, result, gpx) = withContext(Dispatchers.Default) {
                compute(rawData, gpxTrack, data.options, params, disabled, ref, preCached = data.pre)
            }
            val latest = current() ?: return@launch
            _state.value = UiState.Ready(latest.copy(result = result, gpx = gpx, params = params, referenceId = ref, disabled = disabled))
        }
    }

    fun updateParam(algorithmId: String, key: String, value: Double) {
        val data = current() ?: return
        val algoParams = (data.params[algorithmId] ?: emptyMap()) + (key to value)
        rerun(newParams = data.params + (algorithmId to algoParams))
    }

    fun resetParams(algorithmId: String) {
        val data = current() ?: return
        rerun(newParams = data.params - algorithmId, immediate = true)
    }

    fun resetAllParams() {
        rerun(newParams = emptyMap(), newRef = null, refChanged = true, immediate = true)
    }

    fun setReference(algorithmId: String?) {
        rerun(newRef = algorithmId, refChanged = true, immediate = true)
    }

    fun setEnabled(algorithmId: String, enabled: Boolean) {
        val data = current() ?: return
        val disabled = if (enabled) data.disabled - algorithmId else data.disabled + algorithmId
        rerun(newDisabled = disabled, immediate = true)
    }

    /** Change preprocessing (band / sample rate / units): re-filter everything. */
    fun updateOptions(options: PreprocessOptions) {
        val data = current() ?: return
        val rawData = raw ?: return
        _state.value = UiState.Ready(data.copy(options = options, busy = true, dirty = true))
        rerunJob?.cancel()
        rerunJob = viewModelScope.launch {
            try {
                val (pre, result, gpx) = withContext(Dispatchers.Default) {
                    compute(rawData, gpxTrack, options, data.params, data.disabled, data.referenceId)
                }
                val latest = current() ?: return@launch
                _state.value = UiState.Ready(latest.copy(pre = pre, result = result, gpx = gpx, options = options, busy = false))
            } catch (e: Exception) {
                _state.value = UiState.Error(e.message ?: e.toString())
            }
        }
    }

    /** Persist the current run and refresh the session summary. */
    fun saveRun() {
        val data = current() ?: return
        viewModelScope.launch {
            val id = withContext(Dispatchers.IO) { container.sessions.saveRun(data.session, data.result, data.params, data.options) }
            val latest = current() ?: return@launch
            _state.value = UiState.Ready(latest.copy(dirty = false, savedRunId = id, session = container.sessions.get(sessionId) ?: latest.session))
        }
    }

    /** Store the current parameters of one algorithm as the default for future analyses. */
    fun saveParamsAsDefault(algorithmId: String) {
        val data = current() ?: return
        val p = data.params[algorithmId]
        viewModelScope.launch(Dispatchers.IO) {
            if (p == null) container.settings.resetAlgorithmParams(algorithmId) else container.settings.saveAlgorithmParams(algorithmId, p)
            container.settings.setAlgorithmEnabled(algorithmId, algorithmId !in data.disabled)
        }
    }

    fun saveOptionsAsDefault() {
        val data = current() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            container.settings.update {
                it.copy(
                    fsTargetHz = data.options.fsTarget,
                    bandLowHz = data.options.bandLowHz,
                    bandHighHz = data.options.bandHighHz,
                    accelUnit = data.options.accelUnit,
                    gyroUnit = data.options.gyroUnit,
                )
            }
        }
    }

    fun rename(name: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { container.sessions.rename(sessionId, name) }
            val latest = current() ?: return@launch
            _state.value = UiState.Ready(latest.copy(session = container.sessions.get(sessionId) ?: latest.session))
        }
    }

    fun attachGpx(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { container.sessions.attachGpx(sessionId, it) }
                }
                load()
            } catch (e: Exception) {
                _state.value = UiState.Error(e.message ?: e.toString())
            }
        }
    }

    fun removeGpx() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { container.sessions.removeGpx(sessionId) }
            load()
        }
    }

    /** Files to share: CSV plus the (re-generated) GPX with analysis extensions. */
    suspend fun exportFiles(): List<File> = withContext(Dispatchers.IO) {
        val data = current() ?: return@withContext emptyList()
        val out = ArrayList<File>()
        container.sessions.exportCsv(data.session)?.let(out::add)
        val ext = HashMap<String, String>()
        ext["steps"] = data.result.recommended.stepCount.toString()
        ext["algorithm"] = data.result.recommended.algorithmId
        data.result.stepLength.recommendedM?.let { ext["step_length_m"] = Formatters.number(it, 3) }
        data.gpx?.let { ext["distance_m"] = Formatters.number(it.distanceM, 1); ext["duration_sec"] = Formatters.number(it.durationSec, 0) }
        container.sessions.exportGpx(data.session, ext)?.let(out::add)
        out
    }
}
