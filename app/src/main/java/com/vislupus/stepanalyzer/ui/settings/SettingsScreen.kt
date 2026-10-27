package com.vislupus.stepanalyzer.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import com.vislupus.stepanalyzer.AppContainer
import com.vislupus.stepanalyzer.R
import com.vislupus.stepanalyzer.config.AppConfig
import com.vislupus.stepanalyzer.config.AppLanguage
import com.vislupus.stepanalyzer.config.FontScale
import com.vislupus.stepanalyzer.config.Palette
import com.vislupus.stepanalyzer.config.ThemeMode
import com.vislupus.stepanalyzer.core.algorithms.Algorithms
import com.vislupus.stepanalyzer.core.model.AccelUnit
import com.vislupus.stepanalyzer.core.model.GyroUnit
import com.vislupus.stepanalyzer.core.model.Sex
import com.vislupus.stepanalyzer.ui.analysis.gyroUnitLabel
import com.vislupus.stepanalyzer.ui.analysis.unitLabel
import com.vislupus.stepanalyzer.ui.components.AppCard
import com.vislupus.stepanalyzer.ui.components.CardHeader
import com.vislupus.stepanalyzer.ui.components.ChoiceChips
import com.vislupus.stepanalyzer.ui.components.ColorSwatch
import com.vislupus.stepanalyzer.ui.components.ConfirmDialog
import com.vislupus.stepanalyzer.ui.components.LabeledSlider
import com.vislupus.stepanalyzer.ui.components.SectionLabel
import com.vislupus.stepanalyzer.ui.components.SettingsRow
import com.vislupus.stepanalyzer.ui.theme.isDarkTheme
import com.vislupus.stepanalyzer.util.AlgorithmText
import com.vislupus.stepanalyzer.util.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Appearance, profile, recording, analysis defaults, data management and about. */
@Composable
fun SettingsScreen(container: AppContainer, contentPadding: PaddingValues, onOpenInfo: () -> Unit) {
    val repo = container.settings
    val settings by repo.settings.collectAsState()
    val scope = rememberCoroutineScope()
    var confirmClear by remember { mutableStateOf(false) }
    var storageBytes by remember { mutableStateOf(-1L) }
    var runCount by remember { mutableStateOf(0) }
    val sessionCount by container.sessions.sessions.collectAsState()

    LaunchedEffect(sessionCount.size) {
        storageBytes = withContext(Dispatchers.IO) { container.sessions.storageBytes() }
        runCount = withContext(Dispatchers.IO) { container.sessions.runCount() }
    }

    fun io(block: () -> Unit) = scope.launch(Dispatchers.IO) { block() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = AppConfig.Ui.screenPadding),
        verticalArrangement = Arrangement.spacedBy(AppConfig.Ui.cardSpacing),
    ) {
        Spacer(Modifier.height(4.dp))

        // ---- appearance
        AppCard {
            CardHeader(stringResource(R.string.settings_appearance), icon = R.drawable.ic_palette)
            SectionLabel(stringResource(R.string.settings_theme))
            ChoiceChips(
                ThemeMode.entries, settings.themeMode, { io { repo.setTheme(it) } },
                label = {
                    when (it) {
                        ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
                        ThemeMode.LIGHT -> stringResource(R.string.theme_light)
                        ThemeMode.DARK -> stringResource(R.string.theme_dark)
                    }
                },
                icon = {
                    when (it) {
                        ThemeMode.SYSTEM -> R.drawable.ic_monitor
                        ThemeMode.LIGHT -> R.drawable.ic_sun
                        ThemeMode.DARK -> R.drawable.ic_moon
                    }
                },
            )
            SectionLabel(stringResource(R.string.settings_accent))
            val dark = isDarkTheme(settings.themeMode)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (a in Palette.accents) {
                    ColorSwatch(if (dark) a.dark else a.light, selected = a.id == settings.accentId) { io { repo.setAccent(a.id) } }
                }
            }
            SectionLabel(stringResource(R.string.settings_font_size))
            ChoiceChips(
                FontScale.entries, settings.fontScale, { io { repo.setFontScale(it) } },
                label = {
                    when (it) {
                        FontScale.SMALL -> stringResource(R.string.font_small)
                        FontScale.DEFAULT -> stringResource(R.string.font_default)
                        FontScale.LARGE -> stringResource(R.string.font_large)
                        FontScale.XLARGE -> stringResource(R.string.font_xlarge)
                    }
                },
            )
            SectionLabel(stringResource(R.string.settings_language))
            ChoiceChips(
                AppLanguage.entries, settings.language,
                onSelect = { lang ->
                    io { repo.setLanguage(lang) }
                    AppCompatDelegate.setApplicationLocales(
                        if (lang == AppLanguage.SYSTEM) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(lang.tag)
                    )
                },
                label = {
                    when (it) {
                        AppLanguage.SYSTEM -> stringResource(R.string.language_system)
                        AppLanguage.ENGLISH -> "English"
                        AppLanguage.BULGARIAN -> "Български"
                    }
                },
                icon = { R.drawable.ic_languages },
            )
        }

        // ---- profile
        AppCard {
            CardHeader(stringResource(R.string.settings_profile), icon = R.drawable.ic_user, subtitle = stringResource(R.string.settings_profile_hint))
            Spacer(Modifier.height(8.dp))
            var height by remember(settings.heightCm) { mutableStateOf(settings.heightCm.toDouble()) }
            LabeledSlider(
                label = stringResource(R.string.settings_height), value = height, min = 120.0, max = 220.0, step = 1.0,
                onValueChange = { height = it }, onValueChangeFinished = { io { repo.setHeight(height.toInt()) } },
                valueText = "${height.toInt()} cm",
            )
            SectionLabel(stringResource(R.string.settings_sex))
            ChoiceChips(
                Sex.entries, settings.sex, { io { repo.setSex(it) } },
                label = {
                    when (it) {
                        Sex.UNKNOWN -> stringResource(R.string.sex_unknown)
                        Sex.MALE -> stringResource(R.string.sex_male)
                        Sex.FEMALE -> stringResource(R.string.sex_female)
                    }
                },
            )
        }

        // ---- recording
        AppCard {
            CardHeader(stringResource(R.string.settings_recording), icon = R.drawable.ic_activity)
            SectionLabel(stringResource(R.string.settings_sensor_rate))
            ChoiceChips(
                AppConfig.Recording.SENSOR_RATE_CHOICES_HZ, 1_000_000 / settings.sensorPeriodUs,
                { hz -> io { repo.update { s -> s.copy(sensorPeriodUs = 1_000_000 / hz) } } }, { "$it Hz" },
            )
            Spacer(Modifier.height(4.dp))
            SwitchRow(stringResource(R.string.settings_record_gps), stringResource(R.string.settings_record_gps_hint), settings.recordGps) { v -> io { repo.update { it.copy(recordGps = v) } } }
            if (settings.recordGps) {
                SectionLabel(stringResource(R.string.settings_gps_interval))
                ChoiceChips(AppConfig.Recording.GPS_INTERVAL_CHOICES_MS, settings.gpsIntervalMs, { ms -> io { repo.update { it.copy(gpsIntervalMs = ms) } } }, { "${it / 1000} s" })
            }
            SwitchRow(stringResource(R.string.settings_hw_steps), stringResource(R.string.settings_hw_steps_hint), settings.useHardwareStepSensors) { v -> io { repo.update { it.copy(useHardwareStepSensors = v) } } }
            SwitchRow(stringResource(R.string.settings_keep_screen_on), null, settings.keepScreenOn) { v -> io { repo.update { it.copy(keepScreenOn = v) } } }
        }

        // ---- analysis defaults
        AppCard {
            CardHeader(stringResource(R.string.settings_analysis), icon = R.drawable.ic_sliders_horizontal, subtitle = stringResource(R.string.settings_analysis_hint))
            Spacer(Modifier.height(8.dp))
            var low by remember(settings.bandLowHz) { mutableStateOf(settings.bandLowHz) }
            var high by remember(settings.bandHighHz) { mutableStateOf(settings.bandHighHz) }
            LabeledSlider(
                label = stringResource(R.string.settings_band_low), value = low, min = AppConfig.Analysis.BAND_LOW_MIN, max = AppConfig.Analysis.BAND_LOW_MAX, step = 0.1,
                onValueChange = { low = it }, onValueChangeFinished = { io { repo.update { it.copy(bandLowHz = low) } } }, valueText = Formatters.number(low, 1) + " Hz",
            )
            LabeledSlider(
                label = stringResource(R.string.settings_band_high), value = high, min = AppConfig.Analysis.BAND_HIGH_MIN, max = AppConfig.Analysis.BAND_HIGH_MAX, step = 0.5,
                onValueChange = { high = it }, onValueChangeFinished = { io { repo.update { it.copy(bandHighHz = high) } } }, valueText = Formatters.number(high, 1) + " Hz",
            )
            var maxSpeed by remember(settings.maxGpsSpeedMps) { mutableStateOf(settings.maxGpsSpeedMps) }
            LabeledSlider(
                label = stringResource(R.string.settings_max_gps_speed), value = maxSpeed, min = 2.0, max = 20.0, step = 0.5,
                onValueChange = { maxSpeed = it }, onValueChangeFinished = { io { repo.update { it.copy(maxGpsSpeedMps = maxSpeed) } } }, valueText = Formatters.number(maxSpeed, 1) + " m/s",
            )
            SectionLabel(stringResource(R.string.settings_fs_target))
            ChoiceChips(AppConfig.Analysis.FS_CHOICES_HZ, settings.fsTargetHz, { fs -> io { repo.update { it.copy(fsTargetHz = fs) } } }, { "${it.toInt()} Hz" })
            SectionLabel(stringResource(R.string.settings_accel_unit))
            ChoiceChips(AccelUnit.entries, settings.accelUnit, { io { repo.setAccelUnit(it) } }, { unitLabel(it) })
            SectionLabel(stringResource(R.string.settings_gyro_unit))
            ChoiceChips(GyroUnit.entries, settings.gyroUnit, { io { repo.setGyroUnit(it) } }, { gyroUnitLabel(it) })
            SectionLabel(stringResource(R.string.settings_algorithms))
            for (algo in Algorithms.all) {
                val enabled = algo.id !in settings.disabledAlgorithms
                val custom = settings.algorithmParams[algo.id]?.isNotEmpty() == true
                SwitchRow(
                    stringResource(AlgorithmText.name(algo.id)),
                    if (custom) stringResource(R.string.settings_custom_params) else null,
                    enabled,
                ) { v -> io { repo.setAlgorithmEnabled(algo.id, v) } }
            }
            if (settings.algorithmParams.isNotEmpty()) {
                SettingsRow(stringResource(R.string.settings_reset_params), R.drawable.ic_rotate_ccw, onClick = { io { repo.update { it.copy(algorithmParams = emptyMap()) } } })
            }
        }

        // ---- data
        AppCard {
            CardHeader(stringResource(R.string.settings_data), icon = R.drawable.ic_database)
            SettingsRow(stringResource(R.string.settings_storage_folder), R.drawable.ic_folder, subtitle = container.files.root.absolutePath)
            SettingsRow(stringResource(R.string.settings_database), R.drawable.ic_hard_drive, subtitle = container.database.file.name + "  ·  " + stringResource(R.string.settings_db_summary, sessionCount.size, runCount))
            SettingsRow(stringResource(R.string.settings_storage_used), R.drawable.ic_layers, subtitle = if (storageBytes >= 0) Formatters.fileSize(storageBytes) else "…")
            SettingsRow(stringResource(R.string.settings_clear_all), R.drawable.ic_trash_2, subtitle = stringResource(R.string.settings_clear_all_hint), onClick = { confirmClear = true })
        }

        // ---- about
        AppCard {
            CardHeader(stringResource(R.string.settings_about), icon = R.drawable.ic_info)
            SettingsRow(stringResource(R.string.app_name), R.drawable.ic_footprints, subtitle = stringResource(R.string.about_version, versionName()))
            if (AppConfig.Features.SHOW_ALGORITHM_INFO) {
                SettingsRow(stringResource(R.string.info_title), R.drawable.ic_book_open, subtitle = stringResource(R.string.info_subtitle), onClick = onOpenInfo)
            }
            Text(stringResource(R.string.about_text), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        }
        Spacer(Modifier.height(24.dp))
    }

    if (confirmClear) {
        ConfirmDialog(
            title = stringResource(R.string.settings_clear_all),
            text = stringResource(R.string.settings_clear_all_confirm),
            confirmText = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = {
                io {
                    container.sessions.deleteAll()
                    container.files.clearExports()
                    repo.reload()
                }
            },
            onDismiss = { confirmClear = false },
        )
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun versionName(): String {
    val context = androidx.compose.ui.platform.LocalContext.current
    return try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
    } catch (e: Exception) {
        "1.0"
    }
}
