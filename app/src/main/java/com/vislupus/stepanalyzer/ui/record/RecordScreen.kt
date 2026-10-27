package com.vislupus.stepanalyzer.ui.record

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.vislupus.stepanalyzer.AppContainer
import com.vislupus.stepanalyzer.R
import com.vislupus.stepanalyzer.config.AppConfig
import com.vislupus.stepanalyzer.recording.RecordingBus
import com.vislupus.stepanalyzer.recording.RecordingService
import com.vislupus.stepanalyzer.recording.RecordingStatus
import com.vislupus.stepanalyzer.ui.components.AppCard
import com.vislupus.stepanalyzer.ui.components.CardHeader
import com.vislupus.stepanalyzer.ui.components.LucideIcon
import com.vislupus.stepanalyzer.ui.components.StatTile
import com.vislupus.stepanalyzer.ui.components.TextInputDialog
import com.vislupus.stepanalyzer.ui.components.charts.LiveChart
import com.vislupus.stepanalyzer.ui.theme.extra
import com.vislupus.stepanalyzer.util.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Start/stop recording, live counters and the rolling acceleration chart. The actual work
 * happens in [RecordingService]; this screen only observes [RecordingBus].
 */
@Composable
fun RecordScreen(
    container: AppContainer,
    contentPadding: PaddingValues,
    onOpenSession: (Long) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by RecordingBus.state.collectAsState()
    val settings by container.settings.settings.collectAsState()
    var pendingSave by remember { mutableStateOf<Long?>(null) }

    // keep the screen on while recording (if enabled in settings)
    val view = LocalView.current
    LaunchedEffect(state.isRecording, settings.keepScreenOn) {
        view.keepScreenOn = state.isRecording && settings.keepScreenOn
    }

    // when a recording finishes, offer to name it
    LaunchedEffect(state.lastSavedSessionId) {
        state.lastSavedSessionId?.let {
            pendingSave = it
            RecordingBus.consumeSaved()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        RecordingService.start(context)
    }

    fun requiredPermissions(): List<String> {
        val list = ArrayList<String>()
        if (settings.recordGps && AppConfig.Features.ENABLE_GPS) {
            list.add(Manifest.permission.ACCESS_FINE_LOCATION)
            list.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (settings.useHardwareStepSensors && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) list.add(Manifest.permission.ACTIVITY_RECOGNITION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) list.add(Manifest.permission.POST_NOTIFICATIONS)
        return list.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
    }

    fun toggle() {
        if (state.isRecording) {
            RecordingService.stop(context)
        } else if (state.status == RecordingStatus.IDLE) {
            val missing = requiredPermissions()
            if (missing.isEmpty()) RecordingService.start(context) else permissionLauncher.launch(missing.toTypedArray())
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = AppConfig.Ui.screenPadding),
        verticalArrangement = Arrangement.spacedBy(AppConfig.Ui.cardSpacing),
    ) {
        Spacer(Modifier.height(4.dp))

        // ---- status + timer card
        AppCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (state.isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(
                        when (state.status) {
                            RecordingStatus.IDLE -> R.string.record_status_idle
                            RecordingStatus.RECORDING -> R.string.record_status_recording
                            RecordingStatus.STOPPING -> R.string.record_status_saving
                        }
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                Formatters.duration(state.elapsedMs),
                style = MaterialTheme.typography.displayLarge.copy(fontSize = AppConfig.Ui.bigNumberSize, fontWeight = FontWeight.SemiBold),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            LiveChart(
                samples = state.liveSignal,
                capacity = AppConfig.Recording.LIVE_CHART_SECONDS * 100,
                threshold = AppConfig.Recording.Live.THRESHOLD_MPS2.toFloat(),
            )
            Text(
                stringResource(R.string.record_live_chart_caption, AppConfig.Recording.LIVE_CHART_SECONDS),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }

        // ---- counters
        Row(horizontalArrangement = Arrangement.spacedBy(AppConfig.Ui.cardSpacing)) {
            StatTile(
                label = stringResource(R.string.record_live_steps), value = state.liveSteps.toString(),
                icon = R.drawable.ic_footprints, modifier = Modifier.weight(1f), valueColor = MaterialTheme.colorScheme.primary,
            )
            StatTile(
                label = stringResource(R.string.record_hw_steps),
                value = if (state.hwStepCounter >= 0) state.hwStepCounter.toString() else "–",
                icon = R.drawable.ic_smartphone, modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(AppConfig.Ui.cardSpacing)) {
            StatTile(
                label = stringResource(R.string.record_sample_rate), value = Formatters.number(state.accelRateHz, 0), unit = "Hz",
                icon = R.drawable.ic_activity, modifier = Modifier.weight(1f),
            )
            StatTile(
                label = stringResource(R.string.record_samples),
                value = formatCount(state.counts.values.sum()),
                icon = R.drawable.ic_database, modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(AppConfig.Ui.cardSpacing)) {
            StatTile(
                label = stringResource(R.string.record_gps_distance),
                value = if (state.gpsEnabled) Formatters.distance(state.gpsDistanceM) else stringResource(R.string.off),
                icon = R.drawable.ic_route, modifier = Modifier.weight(1f),
            )
            StatTile(
                label = stringResource(R.string.record_gps_points),
                value = if (!state.gpsEnabled) "–" else if (state.gpsHasFix) "${state.gpsPoints} · ±${Formatters.number(state.gpsAccuracyM.toDouble(), 0)} m" else stringResource(R.string.record_gps_waiting),
                icon = R.drawable.ic_satellite, modifier = Modifier.weight(1f),
            )
        }

        // ---- sensors present
        AppCard {
            CardHeader(stringResource(R.string.record_sensors_title), icon = R.drawable.ic_cpu)
            Spacer(Modifier.height(8.dp))
            val names = AppConfig.Recording.SENSORS.map { it.second }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                for (n in names) {
                    val available = if (state.isRecording) n in state.availableSensors else null
                    val count = state.counts[n] ?: 0
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            sensorLabel(n),
                            style = MaterialTheme.typography.labelMedium,
                            color = when (available) {
                                true -> MaterialTheme.extra.success
                                false -> MaterialTheme.colorScheme.error
                                null -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                        Text(formatCount(count), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.record_settings_hint, 1_000_000 / settings.sensorPeriodUs, if (settings.recordGps) stringResource(R.string.on) else stringResource(R.string.off)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        state.error?.let { err ->
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LucideIcon(R.drawable.ic_triangle_alert, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(10.dp))
                    Text(err, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                    TextButton(onClick = { RecordingBus.consumeError() }) { Text(stringResource(R.string.action_dismiss)) }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // ---- big start/stop button
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FilledIconButton(
                    onClick = { toggle() },
                    enabled = state.status != RecordingStatus.STOPPING,
                    modifier = Modifier.size(AppConfig.Ui.recordButtonSize),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (state.isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        contentColor = if (state.isRecording) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    LucideIcon(
                        if (state.isRecording) R.drawable.ic_square else R.drawable.ic_play,
                        tint = if (state.isRecording) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary,
                        size = 36.dp,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(if (state.isRecording) R.string.action_stop else R.string.action_start),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    pendingSave?.let { id ->
        val session = remember(id) { container.sessions.get(id) }
        TextInputDialog(
            title = stringResource(R.string.record_save_title),
            initial = session?.name ?: "",
            confirmText = stringResource(R.string.action_open),
            label = stringResource(R.string.session_name),
            onConfirm = { name ->
                // rename on a background thread, then open the analysis
                scope.launch { withContext(Dispatchers.IO) { container.sessions.rename(id, name) } }
                onOpenSession(id)
            },
            onDismiss = { pendingSave = null },
        )
    }
}

@Composable
private fun sensorLabel(csvName: String): String = when (csvName) {
    "accel" -> stringResource(R.string.sensor_accel_short)
    "uaccel" -> stringResource(R.string.sensor_uaccel_short)
    "gyro" -> stringResource(R.string.sensor_gyro_short)
    "mag" -> stringResource(R.string.sensor_mag_short)
    else -> csvName
}

private fun formatCount(n: Int): String = when {
    n >= 1_000_000 -> String.format(java.util.Locale.US, "%.1fM", n / 1e6)
    n >= 10_000 -> String.format(java.util.Locale.US, "%.0fk", n / 1e3)
    n >= 1_000 -> String.format(java.util.Locale.US, "%.1fk", n / 1e3)
    else -> n.toString()
}
