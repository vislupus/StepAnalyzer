package com.vislupus.stepanalyzer.ui.analysis

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vislupus.stepanalyzer.AppContainer
import com.vislupus.stepanalyzer.R
import com.vislupus.stepanalyzer.config.AppConfig
import com.vislupus.stepanalyzer.ui.components.EmptyState
import com.vislupus.stepanalyzer.ui.components.LucideIcon
import com.vislupus.stepanalyzer.ui.components.TextInputDialog
import com.vislupus.stepanalyzer.util.VmFactory
import kotlinx.coroutines.launch

/** Full analysis of one session. */
@Composable
fun AnalysisScreen(
    container: AppContainer,
    sessionId: Long,
    onBack: () -> Unit,
    onOpenInfo: () -> Unit,
) {
    val vm: AnalysisViewModel = viewModel(key = "analysis-$sessionId", factory = VmFactory<AnalysisViewModel> { AnalysisViewModel(container, sessionId) })
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var selectedAlgorithm by remember { mutableStateOf<String?>(null) }
    var showPreprocess by remember { mutableStateOf(false) }

    val gpxLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.attachGpx(context, uri)
    }

    fun share() {
        scope.launch {
            val files = vm.exportFiles()
            if (files.isEmpty()) return@launch
            val uris = files.map { FileProvider.getUriForFile(context, "${context.packageName}.files", it) }
            val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, context.getString(R.string.action_share)))
        }
    }

    val title = (state as? AnalysisViewModel.UiState.Ready)?.data?.session?.name ?: stringResource(R.string.analysis_title)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { LucideIcon(R.drawable.ic_arrow_left, tint = MaterialTheme.colorScheme.onSurface) } },
                actions = {
                    val ready = state as? AnalysisViewModel.UiState.Ready
                    if (ready != null && ready.data.dirty) {
                        IconButton(onClick = { vm.saveRun() }) { LucideIcon(R.drawable.ic_save, tint = MaterialTheme.colorScheme.primary) }
                    }
                    IconButton(onClick = { menu = true }) { LucideIcon(R.drawable.ic_ellipsis_vertical, tint = MaterialTheme.colorScheme.onSurface) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.action_rename)) }, onClick = { menu = false; renaming = true },
                            leadingIcon = { LucideIcon(R.drawable.ic_pencil, size = 18.dp) })
                        DropdownMenuItem(text = { Text(stringResource(R.string.analysis_preprocessing)) }, onClick = { menu = false; showPreprocess = true },
                            leadingIcon = { LucideIcon(R.drawable.ic_sliders_horizontal, size = 18.dp) })
                        DropdownMenuItem(text = { Text(stringResource(R.string.action_attach_gpx)) }, onClick = { menu = false; gpxLauncher.launch(arrayOf("*/*")) },
                            leadingIcon = { LucideIcon(R.drawable.ic_map_pin, size = 18.dp) })
                        if (ready?.data?.gpx != null) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.action_remove_gpx)) }, onClick = { menu = false; vm.removeGpx() },
                                leadingIcon = { LucideIcon(R.drawable.ic_x, size = 18.dp) })
                        }
                        DropdownMenuItem(text = { Text(stringResource(R.string.action_reset_all_params)) }, onClick = { menu = false; vm.resetAllParams() },
                            leadingIcon = { LucideIcon(R.drawable.ic_rotate_ccw, size = 18.dp) })
                        if (AppConfig.Features.ENABLE_SHARE) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.action_share)) }, onClick = { menu = false; share() },
                                leadingIcon = { LucideIcon(R.drawable.ic_share_2, size = 18.dp) })
                        }
                        if (AppConfig.Features.SHOW_ALGORITHM_INFO) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.info_title)) }, onClick = { menu = false; onOpenInfo() },
                                leadingIcon = { LucideIcon(R.drawable.ic_book_open, size = 18.dp) })
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        when (val s = state) {
            is AnalysisViewModel.UiState.Loading -> LoadingView(s.stage, padding)
            is AnalysisViewModel.UiState.Error -> Column(Modifier.padding(padding)) {
                EmptyState(R.drawable.ic_triangle_alert, stringResource(R.string.analysis_error_title), s.message)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    FilledTonalButton(onClick = { vm.load() }) { Text(stringResource(R.string.action_retry)) }
                }
            }
            is AnalysisViewModel.UiState.Ready -> AnalysisContent(
                data = s.data,
                padding = padding,
                onAlgorithmClick = { selectedAlgorithm = it },
                onAttachGpx = { gpxLauncher.launch(arrayOf("*/*")) },
                onPreprocess = { showPreprocess = true },
            )
        }
    }

    (state as? AnalysisViewModel.UiState.Ready)?.data?.let { data ->
        selectedAlgorithm?.let { id ->
            AlgorithmSheet(
                data = data,
                algorithmId = id,
                onDismiss = { selectedAlgorithm = null },
                onParam = { key, value -> vm.updateParam(id, key, value) },
                onReset = { vm.resetParams(id) },
                onSaveDefault = { vm.saveParamsAsDefault(id) },
                onEnabled = { vm.setEnabled(id, it) },
                onReference = { vm.setReference(if (it) id else null) },
            )
        }
        if (showPreprocess) {
            PreprocessSheet(
                data = data,
                onDismiss = { showPreprocess = false },
                onApply = { vm.updateOptions(it) },
                onSaveDefault = { vm.saveOptionsAsDefault() },
            )
        }
        if (renaming) {
            TextInputDialog(
                title = stringResource(R.string.action_rename),
                initial = data.session.name,
                confirmText = stringResource(R.string.action_save),
                label = stringResource(R.string.session_name),
                onConfirm = { vm.rename(it) },
                onDismiss = { renaming = false },
            )
        }
    }
}

@Composable
private fun LoadingView(stage: Int, padding: PaddingValues) {
    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(
                    when (stage) {
                        0 -> R.string.analysis_loading_session
                        1 -> R.string.analysis_loading_csv
                        else -> R.string.analysis_loading_compute
                    }
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AnalysisContent(
    data: AnalysisViewModel.Data,
    padding: PaddingValues,
    onAlgorithmClick: (String) -> Unit,
    onAttachGpx: () -> Unit,
    onPreprocess: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(horizontal = AppConfig.Ui.screenPadding, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(AppConfig.Ui.cardSpacing),
    ) {
        if (data.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        item { SummaryCard(data) }
        item { SignalCard(data) }
        item { CadenceCard(data) }
        item { AlgorithmsCard(data, onAlgorithmClick) }
        item { CompareCard(data) }
        item { StepLengthCard(data) }
        item { GpxCard(data, onAttachGpx) }
        item { FileInfoCard(data, onPreprocess) }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** Small label/value row used in several cards. */
@Composable
internal fun KeyValueRow(label: String, value: String, highlight: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
internal fun CardDivider() {
    HorizontalDivider(Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
internal fun InlineAction(text: String, icon: Int, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        LucideIcon(icon, size = 16.dp, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(6.dp))
        Text(text)
    }
}
