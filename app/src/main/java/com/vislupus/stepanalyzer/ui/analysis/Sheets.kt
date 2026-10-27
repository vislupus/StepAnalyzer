package com.vislupus.stepanalyzer.ui.analysis

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vislupus.stepanalyzer.R
import com.vislupus.stepanalyzer.config.AppConfig
import com.vislupus.stepanalyzer.core.algorithms.Algorithms
import com.vislupus.stepanalyzer.core.model.AccelUnit
import com.vislupus.stepanalyzer.core.model.GyroUnit
import com.vislupus.stepanalyzer.core.model.PreprocessOptions
import com.vislupus.stepanalyzer.ui.components.ChoiceChips
import com.vislupus.stepanalyzer.ui.components.ConfidenceBar
import com.vislupus.stepanalyzer.ui.components.LabeledSlider
import com.vislupus.stepanalyzer.ui.components.SectionLabel
import com.vislupus.stepanalyzer.ui.components.StatTile
import com.vislupus.stepanalyzer.util.AlgorithmText
import com.vislupus.stepanalyzer.util.Formatters

/** Bottom sheet with the description, live result and parameter sliders of one algorithm. */
@Composable
internal fun AlgorithmSheet(
    data: AnalysisViewModel.Data,
    algorithmId: String,
    onDismiss: () -> Unit,
    onParam: (String, Double) -> Unit,
    onReset: () -> Unit,
    onSaveDefault: () -> Unit,
    onEnabled: (Boolean) -> Unit,
    onReference: (Boolean) -> Unit,
) {
    val algo = Algorithms.byId(algorithmId) ?: return
    val result = data.result.algorithm(algorithmId)
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val enabled = algorithmId !in data.disabled
    val isReference = data.result.referenceAlgorithmId == algorithmId
    val current = data.params[algorithmId] ?: emptyMap()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppConfig.Ui.screenPadding)
                .padding(bottom = 32.dp),
        ) {
            Text(stringResource(AlgorithmText.name(algorithmId)), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(AlgorithmText.family(algo.family)) + (if (algo.requires.isNotEmpty()) "  ·  " + algo.requires.map { stringResource(AlgorithmText.sensor(it)) }.joinToString() else ""),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            Text(stringResource(AlgorithmText.description(algorithmId)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))

            if (result != null && result.available && enabled) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile(stringResource(R.string.unit_steps), result.stepCount.toString(), Modifier.weight(1f), valueColor = MaterialTheme.colorScheme.primary)
                    StatTile(stringResource(R.string.analysis_cadence_short), Formatters.number(result.cadenceSpm, 0), Modifier.weight(1f), unit = stringResource(R.string.unit_spm))
                    StatTile(stringResource(R.string.analysis_gpx_length_short), result.avgStepLengthGpxM?.let { Formatters.number(it, 2) } ?: "–", Modifier.weight(1f), unit = "m")
                }
                Spacer(Modifier.height(8.dp))
                ConfidenceBar(result.confidence)
                if (result.notes.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(result.notes.joinToString("  ·  ") { AlgorithmText.note(context, it) }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else if (result != null && !result.available) {
                Text(result.notes.joinToString { AlgorithmText.note(context, it) }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.algo_enabled), modifier = Modifier.weight(1f))
                Switch(checked = enabled, onCheckedChange = onEnabled)
            }
            if (result != null && result.hasStepIndices) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.algo_use_as_reference))
                        Text(stringResource(R.string.algo_use_as_reference_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = isReference, onCheckedChange = onReference, enabled = enabled)
                }
            }

            Spacer(Modifier.height(8.dp))
            SectionLabel(stringResource(R.string.algo_parameters))
            for (spec in algo.paramSpecs) {
                val value = current[spec.key] ?: spec.default
                LabeledSlider(
                    label = stringResource(AlgorithmText.param(spec.key)),
                    value = value, min = spec.min, max = spec.max, step = spec.step,
                    onValueChange = { onParam(spec.key, it) },
                    valueText = Formatters.param(value, spec.step) + AlgorithmText.unitSuffix(spec.unit) +
                        (if (value != spec.default) "  (${Formatters.param(spec.default, spec.step)})" else ""),
                    enabled = enabled,
                )
            }
            // values derived by the algorithm (e.g. the prominence actually used)
            result?.params?.filterKeys { k -> algo.paramSpecs.none { it.key == k } }?.takeIf { it.isNotEmpty() }?.let { derived ->
                Spacer(Modifier.height(4.dp))
                Text(
                    derived.entries.joinToString("  ·  ") { "${it.key} = ${Formatters.number(it.value, 3)}" },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onReset, modifier = Modifier.weight(1f), enabled = current.isNotEmpty()) { Text(stringResource(R.string.action_reset)) }
                Button(onClick = onSaveDefault, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_save_as_default)) }
            }
        }
    }
}

/** Bottom sheet for the preprocessing options (band-pass, resampling, units). */
@Composable
internal fun PreprocessSheet(
    data: AnalysisViewModel.Data,
    onDismiss: () -> Unit,
    onApply: (PreprocessOptions) -> Unit,
    onSaveDefault: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var options by remember(data.options) { mutableStateOf(data.options) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppConfig.Ui.screenPadding)
                .padding(bottom = 32.dp),
        ) {
            Text(stringResource(R.string.analysis_preprocessing), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.preprocess_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            LabeledSlider(
                label = stringResource(R.string.settings_band_low), value = options.bandLowHz,
                min = AppConfig.Analysis.BAND_LOW_MIN, max = AppConfig.Analysis.BAND_LOW_MAX, step = 0.1,
                onValueChange = { options = options.copy(bandLowHz = it) }, valueText = Formatters.number(options.bandLowHz, 1) + " Hz",
            )
            LabeledSlider(
                label = stringResource(R.string.settings_band_high), value = options.bandHighHz,
                min = AppConfig.Analysis.BAND_HIGH_MIN, max = AppConfig.Analysis.BAND_HIGH_MAX, step = 0.5,
                onValueChange = { options = options.copy(bandHighHz = it) }, valueText = Formatters.number(options.bandHighHz, 1) + " Hz",
            )
            SectionLabel(stringResource(R.string.settings_fs_target))
            ChoiceChips(AppConfig.Analysis.FS_CHOICES_HZ, options.fsTarget, { options = options.copy(fsTarget = it) }, { "${it.toInt()} Hz" })
            SectionLabel(stringResource(R.string.settings_accel_unit))
            ChoiceChips(AccelUnit.entries, options.accelUnit, { options = options.copy(accelUnit = it) }, { unitLabel(it) })
            SectionLabel(stringResource(R.string.settings_gyro_unit))
            ChoiceChips(GyroUnit.entries, options.gyroUnit, { options = options.copy(gyroUnit = it) }, { gyroUnitLabel(it) })
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onSaveDefault(); onApply(options) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_save_as_default)) }
                Button(onClick = { onApply(options); onDismiss() }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_apply)) }
            }
            Spacer(Modifier.width(1.dp))
        }
    }
}

@Composable
internal fun unitLabel(u: AccelUnit): String = when (u) {
    AccelUnit.AUTO -> stringResource(R.string.unit_auto)
    AccelUnit.MPS2 -> "m/s²"
    AccelUnit.G -> "g"
}

@Composable
internal fun gyroUnitLabel(u: GyroUnit): String = when (u) {
    GyroUnit.AUTO -> stringResource(R.string.unit_auto)
    GyroUnit.RADPS -> "rad/s"
    GyroUnit.DEGPS -> "deg/s"
}
