package com.vislupus.stepanalyzer.ui.analysis

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vislupus.stepanalyzer.R
import com.vislupus.stepanalyzer.config.AppConfig
import com.vislupus.stepanalyzer.config.Palette
import com.vislupus.stepanalyzer.core.algorithms.Algorithms
import com.vislupus.stepanalyzer.core.model.AlgorithmResult
import com.vislupus.stepanalyzer.ui.components.AppCard
import com.vislupus.stepanalyzer.ui.components.CardHeader
import com.vislupus.stepanalyzer.ui.components.ConfidenceBar
import com.vislupus.stepanalyzer.ui.components.LucideIcon
import com.vislupus.stepanalyzer.ui.components.StatTile
import com.vislupus.stepanalyzer.ui.components.charts.ChartMarkers
import com.vislupus.stepanalyzer.ui.components.charts.ChartSeries
import com.vislupus.stepanalyzer.ui.components.charts.CompareBar
import com.vislupus.stepanalyzer.ui.components.charts.CompareBars
import com.vislupus.stepanalyzer.ui.components.charts.LineChart
import com.vislupus.stepanalyzer.ui.components.charts.TrackMap
import com.vislupus.stepanalyzer.ui.theme.extra
import com.vislupus.stepanalyzer.util.AlgorithmText
import com.vislupus.stepanalyzer.util.Formatters
import kotlin.math.max
import kotlin.math.min

// ---------------------------------------------------------------- summary

@Composable
internal fun SummaryCard(data: AnalysisViewModel.Data) {
    val r = data.result
    val rec = r.recommended
    AppCard {
        Text(stringResource(R.string.analysis_recommended), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                rec.stepCount.toString(),
                style = MaterialTheme.typography.displayLarge.copy(fontSize = AppConfig.Ui.bigNumberSize, fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.unit_steps), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 10.dp))
        }
        Text(stringResource(AlgorithmText.name(rec.algorithmId)), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        ConfidenceBar(rec.confidence)
        Spacer(Modifier.height(12.dp))
        val sl = r.stepLength.recommendedM
        val gpxSummary = r.gpx
        val distance = when {
            gpxSummary != null -> gpxSummary.distanceM
            sl != null -> sl * rec.stepCount
            else -> null
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(stringResource(R.string.analysis_spread), "${r.summary.minSteps}–${r.summary.maxSteps}", Modifier.weight(1f))
            StatTile(stringResource(R.string.analysis_step_length), sl?.let { Formatters.number(it, 2) } ?: "–", Modifier.weight(1f), unit = "m")
            StatTile(
                stringResource(if (r.gpx != null) R.string.analysis_distance_gps else R.string.analysis_distance_est),
                distance?.let { Formatters.distance(it) } ?: "–", Modifier.weight(1f),
            )
        }
        val s = data.session
        if (s.hwStepCounter >= 0 || s.liveSteps > 0) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (s.hwStepCounter >= 0) StatTile(stringResource(R.string.analysis_phone_counter), s.hwStepCounter.toString(), Modifier.weight(1f), icon = R.drawable.ic_smartphone)
                if (s.liveSteps > 0) StatTile(stringResource(R.string.analysis_live_counter), s.liveSteps.toString(), Modifier.weight(1f), icon = R.drawable.ic_zap)
                if (s.hwStepDetector >= 0) StatTile(stringResource(R.string.analysis_phone_detector), s.hwStepDetector.toString(), Modifier.weight(1f), icon = R.drawable.ic_smartphone)
            }
        }
    }
}

// ---------------------------------------------------------------- signal chart

@Composable
internal fun SignalCard(data: AnalysisViewModel.Data) {
    val pre = data.pre
    val duration = pre.durationS.coerceAtLeast(1.0)
    var showRaw by remember { mutableStateOf(false) }
    var showFiltered by remember { mutableStateOf(true) }
    var showSteps by remember { mutableStateOf(true) }
    var showAlgoSignal by remember { mutableStateOf(false) }
    var window by remember(duration) { mutableStateOf(0f..min(duration, AppConfig.Analysis.DEFAULT_WINDOW_S).toFloat()) }

    val reference = data.result.algorithm(data.result.referenceAlgorithmId)
    val referenceSignal: DoubleArray? = reference?.signal
    val rawColor = MaterialTheme.extra.chartRaw
    val filtColor = MaterialTheme.colorScheme.primary
    val stepColor = MaterialTheme.colorScheme.error
    val altColor = Palette.chartSeries[2]

    AppCard {
        CardHeader(
            stringResource(R.string.analysis_signal_title), icon = R.drawable.ic_audio_waveform,
            subtitle = stringResource(R.string.analysis_signal_subtitle, stringResource(AlgorithmText.shortName(data.result.referenceAlgorithmId))),
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            ToggleChip(stringResource(R.string.chart_filtered), showFiltered, filtColor) { showFiltered = it }
            ToggleChip(stringResource(R.string.chart_raw), showRaw, rawColor) { showRaw = it }
            ToggleChip(stringResource(R.string.chart_steps), showSteps, stepColor) { showSteps = it }
            if (referenceSignal != null) ToggleChip(stringResource(R.string.chart_algo_signal), showAlgoSignal, altColor) { showAlgoSignal = it }
        }
        Spacer(Modifier.height(6.dp))
        val series = ArrayList<ChartSeries>()
        if (showRaw) {
            // raw |a| is around 9.8 m/s^2: subtract the median so it shares the y axis
            val med = com.vislupus.stepanalyzer.core.signal.Stats.median(pre.accMagRaw)
            val centred = remember(pre) { DoubleArray(pre.accMagRaw.size) { pre.accMagRaw[it] - med } }
            series.add(ChartSeries(pre.t, centred, rawColor, 0.8f))
        }
        if (showFiltered) series.add(ChartSeries(pre.t, pre.aFilt, filtColor))
        if (showAlgoSignal && referenceSignal != null) series.add(ChartSeries(pre.t, referenceSignal, altColor))
        val markers = if (showSteps && reference != null && reference.hasStepIndices) {
            val sig = if (showAlgoSignal && referenceSignal != null) referenceSignal else pre.aFilt
            ChartMarkers(
                DoubleArray(reference.stepIndices.size) { pre.t[reference.stepIndices[it]] },
                DoubleArray(reference.stepIndices.size) { sig[reference.stepIndices[it]] },
                stepColor,
            )
        } else null
        LineChart(
            series = series.ifEmpty { listOf(ChartSeries(pre.t, pre.aFilt, filtColor.copy(alpha = 0.2f))) },
            xRange = window.start.toDouble()..window.endInclusive.toDouble(),
            markers = markers,
            yLabel = "m/s²",
        )
        RangeSlider(
            value = window,
            onValueChange = { range ->
                val lo = range.start.coerceIn(0f, duration.toFloat())
                val hi = range.endInclusive.coerceIn(0f, duration.toFloat())
                window = if (hi - lo < 2f) lo..min(duration.toFloat(), lo + 2f) else lo..hi
            },
            valueRange = 0f..duration.toFloat(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "${Formatters.durationSeconds(window.start.toDouble())} – ${Formatters.durationSeconds(window.endInclusive.toDouble())}",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (sec in listOf(10.0, 30.0, 120.0)) {
                    if (sec < duration) {
                        Text(
                            Formatters.durationSeconds(sec), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable {
                                val start = window.start.toDouble().coerceAtMost(max(0.0, duration - sec))
                                window = start.toFloat()..(start + sec).toFloat()
                            },
                        )
                    }
                }
                Text(
                    stringResource(R.string.chart_all), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { window = 0f..duration.toFloat() },
                )
            }
        }
    }
}

@Composable
private fun ToggleChip(label: String, selected: Boolean, color: Color, onChange: (Boolean) -> Unit) {
    FilterChip(
        selected = selected,
        onClick = { onChange(!selected) },
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        leadingIcon = {
            androidx.compose.foundation.Canvas(Modifier.width(10.dp).height(10.dp)) { drawCircle(color) }
        },
    )
}

// ---------------------------------------------------------------- cadence

@Composable
internal fun CadenceCard(data: AnalysisViewModel.Data) {
    val cad = data.result.cadence
    if (cad.tSec.isEmpty()) return
    val color = MaterialTheme.colorScheme.primary
    AppCard {
        CardHeader(stringResource(R.string.analysis_cadence_title), icon = R.drawable.ic_gauge, subtitle = stringResource(R.string.analysis_cadence_subtitle))
        Spacer(Modifier.height(8.dp))
        LineChart(
            series = listOf(ChartSeries(cad.tSec, cad.spm, color)),
            height = AppConfig.Ui.cadenceChartHeight,
            stepped = true,
            fillUnderFirst = true,
            yRangeOverride = 0.0..max(140.0, (cad.spm.maxOrNull() ?: 0.0) * 1.1),
            yLabel = stringResource(R.string.unit_spm),
        )
    }
}

// ---------------------------------------------------------------- algorithms

@Composable
internal fun AlgorithmsCard(data: AnalysisViewModel.Data, onClick: (String) -> Unit) {
    val r = data.result
    val context = LocalContext.current
    AppCard(contentPadding = 0.dp) {
        Column(Modifier.padding(AppConfig.Ui.cardPadding)) {
            CardHeader(
                stringResource(R.string.analysis_algorithms_title), icon = R.drawable.ic_flask_conical,
                subtitle = stringResource(R.string.analysis_algorithms_subtitle),
            )
        }
        val ordered = Algorithms.all.map { algo -> algo.id to r.algorithm(algo.id) }
        for ((id, result) in ordered) {
            val disabled = id in data.disabled
            if (result == null && !disabled) continue
            if (result != null && !result.available && !AppConfig.Ui.SHOW_UNAVAILABLE_ALGORITHMS) continue
            AlgorithmRow(
                id = id,
                result = result,
                disabled = disabled,
                recommended = id == r.recommended.algorithmId,
                reference = id == r.referenceAlgorithmId,
                customised = data.params[id]?.isNotEmpty() == true,
                noteText = result?.notes?.firstOrNull()?.let { AlgorithmText.note(context, it) },
                onClick = { onClick(id) },
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun AlgorithmRow(
    id: String, result: AlgorithmResult?, disabled: Boolean, recommended: Boolean, reference: Boolean,
    customised: Boolean, noteText: String?, onClick: () -> Unit,
) {
    val dim = disabled || result == null || !result.available
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = AppConfig.Ui.cardPadding, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(AlgorithmText.name(id)),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (dim) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (recommended) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                )
                if (recommended) { Spacer(Modifier.width(6.dp)); LucideIcon(R.drawable.ic_circle_check, size = 16.dp, tint = MaterialTheme.extra.success) }
                if (reference) { Spacer(Modifier.width(6.dp)); LucideIcon(R.drawable.ic_target, size = 16.dp, tint = MaterialTheme.colorScheme.primary) }
                if (customised) { Spacer(Modifier.width(6.dp)); LucideIcon(R.drawable.ic_sliders_horizontal, size = 16.dp, tint = MaterialTheme.colorScheme.tertiary) }
            }
            val subtitle = when {
                disabled -> stringResource(R.string.algo_disabled)
                result == null -> ""
                !result.available -> noteText ?: stringResource(R.string.algo_unavailable)
                else -> stringResource(R.string.algo_row_subtitle, Formatters.number(result.cadenceSpm, 0),
                    result.avgStepLengthGpxM?.let { Formatters.number(it, 2) + " m" } ?: "–")
            }
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (result != null && result.available && !disabled) {
                Spacer(Modifier.height(4.dp))
                ConfidenceBar(result.confidence, showLabel = false, modifier = Modifier.fillMaxWidth(0.6f))
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            if (result != null && result.available && !disabled) result.stepCount.toString() else "–",
            style = MaterialTheme.typography.titleLarge,
            color = if (dim) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
        )
        Spacer(Modifier.width(4.dp))
        LucideIcon(R.drawable.ic_chevron_right, size = 18.dp)
    }
}

// ---------------------------------------------------------------- comparison

@Composable
internal fun CompareCard(data: AnalysisViewModel.Data) {
    val r = data.result
    val bars = Algorithms.all.mapIndexedNotNull { i, algo ->
        val res = r.algorithm(algo.id) ?: return@mapIndexedNotNull null
        if (!res.available) return@mapIndexedNotNull null
        CompareBar(
            label = stringResource(AlgorithmText.shortName(algo.id)),
            value = res.stepCount.toDouble(),
            color = Palette.chartSeries[i % Palette.chartSeries.size],
            highlight = algo.id == r.recommended.algorithmId,
        )
    }
    val phoneLabel = stringResource(R.string.analysis_phone_counter)
    val liveLabel = stringResource(R.string.analysis_live_counter)
    val refs: List<Pair<String, Double>> = listOfNotNull(
        if (data.session.hwStepCounter > 0) Pair(phoneLabel, data.session.hwStepCounter.toDouble()) else null,
        if (data.session.liveSteps > 0) Pair(liveLabel, data.session.liveSteps.toDouble()) else null,
    )
    AppCard {
        CardHeader(stringResource(R.string.analysis_compare_title), icon = R.drawable.ic_chart_column, subtitle = stringResource(R.string.analysis_compare_subtitle, r.summary.spread))
        Spacer(Modifier.height(8.dp))
        CompareBars(bars, references = refs)
    }
}

// ---------------------------------------------------------------- step length

@Composable
internal fun StepLengthCard(data: AnalysisViewModel.Data) {
    val sl = data.result.stepLength
    AppCard {
        CardHeader(
            stringResource(R.string.analysis_step_length_title), icon = R.drawable.ic_ruler,
            subtitle = stringResource(R.string.analysis_step_length_subtitle, stringResource(AlgorithmText.shortName(sl.referenceAlgorithmId)), sl.referenceStepCount),
        )
        Spacer(Modifier.height(6.dp))
        for (m in sl.methods) {
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(AlgorithmText.methodName(m.id)), style = MaterialTheme.typography.bodyLarge)
                        if (m.calibrated) { Spacer(Modifier.width(6.dp)); LucideIcon(R.drawable.ic_locate_fixed, size = 14.dp, tint = MaterialTheme.extra.success) }
                    }
                    val calibratedK = m.calibratedK
                    val defaultK = m.defaultK
                    val coefficient = m.coefficient
                    val requirementText = stringResource(AlgorithmText.requirement(m.requires))
                    val detail = buildString {
                        append(requirementText)
                        if (calibratedK != null) append("  ·  K = ${Formatters.number(calibratedK, 3)} (${Formatters.number(defaultK ?: 0.0, 2)} → ${Formatters.number(m.uncalibratedM ?: 0.0, 2)} m)")
                        else if (defaultK != null) append("  ·  K = ${Formatters.number(defaultK, 2)}")
                        if (coefficient != null) append("  ·  × ${Formatters.number(coefficient, 3)}")
                    }
                    Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(Formatters.number(m.stepLengthM, 3) + " m", style = MaterialTheme.typography.titleMedium,
                        color = if (m.id == sl.recommendedSource) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    m.distanceM?.let { Text(Formatters.distance(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
        CardDivider()
        KeyValueRow(stringResource(R.string.analysis_height_used), Formatters.number(sl.heightUsedM, 2) + " m" + if (sl.heightWasDefault) " (${stringResource(R.string.default_value)})" else "")
        KeyValueRow(
            stringResource(R.string.analysis_recommended_length),
            (sl.recommendedM?.let { Formatters.number(it, 3) + " m" } ?: "–") + "  ·  " +
                if (sl.recommendedSource == com.vislupus.stepanalyzer.core.steplength.StepLengthEstimator.GPX_AVG) stringResource(R.string.method_gpx_avg_name) else stringResource(R.string.analysis_median_of_methods),
            highlight = true,
        )
    }
}

// ---------------------------------------------------------------- gpx

@Composable
internal fun GpxCard(data: AnalysisViewModel.Data, onAttach: () -> Unit) {
    val gpx = data.gpx
    AppCard {
        CardHeader(stringResource(R.string.analysis_gpx_title), icon = R.drawable.ic_route,
            subtitle = if (gpx != null) stringResource(R.string.analysis_gpx_subtitle, gpx.nPoints, gpx.nRejected) else stringResource(R.string.analysis_gpx_none))
        if (gpx == null) {
            Spacer(Modifier.height(8.dp))
            InlineAction(stringResource(R.string.action_attach_gpx), R.drawable.ic_map_pin, onAttach)
        } else {
            Spacer(Modifier.height(8.dp))
            TrackMap(gpx.lat, gpx.lon)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(stringResource(R.string.gpx_distance), Formatters.distance(gpx.distanceM), Modifier.weight(1f))
                StatTile(stringResource(R.string.gpx_duration), Formatters.durationSeconds(gpx.durationSec), Modifier.weight(1f))
                StatTile(stringResource(R.string.gpx_speed), Formatters.number(gpx.movingSpeedKmh, 1), Modifier.weight(1f), unit = "km/h")
            }
            Spacer(Modifier.height(6.dp))
            KeyValueRow(stringResource(R.string.gpx_raw_distance), Formatters.distance(gpx.rawDistanceM))
            KeyValueRow(stringResource(R.string.gpx_elevation_gain), Formatters.metres(gpx.elevationGainM, 0))
            val expected = data.result.stepLength.recommendedM?.takeIf { it > 0 }?.let { gpx.distanceM / it }
            if (expected != null) KeyValueRow(stringResource(R.string.gpx_implied_steps), expected.toInt().toString())
        }
    }
}

// ---------------------------------------------------------------- file info

@Composable
internal fun FileInfoCard(data: AnalysisViewModel.Data, onPreprocess: () -> Unit) {
    val fi = data.result.fileInfo
    val pre = data.pre
    AppCard {
        CardHeader(stringResource(R.string.analysis_file_title), icon = R.drawable.ic_file_text,
            trailing = { InlineAction(stringResource(R.string.analysis_preprocessing), R.drawable.ic_sliders_horizontal, onPreprocess) })
        KeyValueRow(stringResource(R.string.file_rows), fi.rows.toString())
        KeyValueRow(stringResource(R.string.file_duration), Formatters.durationSeconds(fi.durationSec))
        KeyValueRow(stringResource(R.string.file_sensors), fi.sensorsFound.joinToString(", ") { it.csvName + " (" + Formatters.number(fi.nativeRatesHz[it] ?: 0.0, 0) + " Hz)" })
        KeyValueRow(stringResource(R.string.file_resampled), Formatters.number(fi.sampleRateHz, 0) + " Hz")
        KeyValueRow(stringResource(R.string.file_band), "${Formatters.number(pre.options.bandLowHz, 1)} – ${Formatters.number(pre.options.bandHighHz, 1)} Hz")
        KeyValueRow(stringResource(R.string.file_units), "${pre.accelUnitDetected.name.lowercase()}" + (pre.gyroUnitDetected?.let { " · gyro ${it.name.lowercase()}" } ?: ""))
        KeyValueRow(stringResource(R.string.file_source), fi.dynamicSource.csvName)
        KeyValueRow(stringResource(R.string.file_periodicity), Formatters.number(data.result.summary.globalPeriodicity, 2))
        if (data.session.notes.isNotBlank()) KeyValueRow(stringResource(R.string.file_device), data.session.notes)
    }
}
