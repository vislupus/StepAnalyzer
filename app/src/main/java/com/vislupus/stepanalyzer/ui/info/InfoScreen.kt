package com.vislupus.stepanalyzer.ui.info

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vislupus.stepanalyzer.R
import com.vislupus.stepanalyzer.config.AppConfig
import com.vislupus.stepanalyzer.core.algorithms.Algorithms
import com.vislupus.stepanalyzer.core.steplength.StepLengthEstimator
import com.vislupus.stepanalyzer.ui.components.AppCard
import com.vislupus.stepanalyzer.ui.components.CardHeader
import com.vislupus.stepanalyzer.ui.components.LucideIcon
import com.vislupus.stepanalyzer.ui.components.SectionLabel
import com.vislupus.stepanalyzer.util.AlgorithmText

/** Reference page describing every algorithm and step-length method. */
@Composable
fun InfoScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.info_title)) },
                navigationIcon = { IconButton(onClick = onBack) { LucideIcon(R.drawable.ic_arrow_left, tint = MaterialTheme.colorScheme.onSurface) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = AppConfig.Ui.screenPadding, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(AppConfig.Ui.cardSpacing),
        ) {
            item {
                AppCard {
                    CardHeader(stringResource(R.string.info_pipeline_title), icon = R.drawable.ic_audio_waveform)
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.info_pipeline_text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item { SectionLabel(stringResource(R.string.info_algorithms_section), Modifier.padding(start = 4.dp)) }
            items(Algorithms.all.size) { i ->
                val algo = Algorithms.all[i]
                AppCard {
                    CardHeader(
                        stringResource(AlgorithmText.name(algo.id)), icon = R.drawable.ic_flask_conical,
                        subtitle = stringResource(AlgorithmText.family(algo.family)) +
                            (if (algo.requires.isNotEmpty()) "  ·  " + algo.requires.map { stringResource(AlgorithmText.sensor(it)) }.joinToString() else ""),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(AlgorithmText.description(algo.id)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        algo.paramSpecs.map { stringResource(AlgorithmText.param(it.key)) }.joinToString("  ·  "),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            item { SectionLabel(stringResource(R.string.info_methods_section), Modifier.padding(start = 4.dp)) }
            val methods = listOf(
                StepLengthEstimator.GPX_AVG, StepLengthEstimator.WEINBERG, StepLengthEstimator.KIM, StepLengthEstimator.SCARLETT,
                StepLengthEstimator.HEIGHT_FACTOR, StepLengthEstimator.CADENCE_LINEAR, StepLengthEstimator.HEIGHT_CADENCE,
            )
            items(methods.size) { i ->
                val id = methods[i]
                AppCard {
                    CardHeader(stringResource(AlgorithmText.methodName(id)), icon = R.drawable.ic_ruler)
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(AlgorithmText.methodDescription(id)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                AppCard {
                    CardHeader(stringResource(R.string.info_confidence_title), icon = R.drawable.ic_percent)
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.info_confidence_text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
