package com.vislupus.stepanalyzer.ui.sessions

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vislupus.stepanalyzer.AppContainer
import com.vislupus.stepanalyzer.R
import com.vislupus.stepanalyzer.config.AppConfig
import com.vislupus.stepanalyzer.data.db.Session
import com.vislupus.stepanalyzer.ui.components.AppCard
import com.vislupus.stepanalyzer.ui.components.ConfirmDialog
import com.vislupus.stepanalyzer.ui.components.EmptyState
import com.vislupus.stepanalyzer.ui.components.LucideIcon
import com.vislupus.stepanalyzer.ui.components.TextInputDialog
import com.vislupus.stepanalyzer.util.AlgorithmText
import com.vislupus.stepanalyzer.util.Formatters
import com.vislupus.stepanalyzer.util.VmFactory

/** List of recordings and imports. */
@Composable
fun SessionsScreen(
    container: AppContainer,
    contentPadding: PaddingValues,
    onOpenSession: (Long) -> Unit,
) {
    val vm: SessionsViewModel = viewModel(factory = VmFactory<SessionsViewModel> { SessionsViewModel(container) })
    val sessions by vm.sessions.collectAsState()
    val busy by vm.busy.collectAsState()
    val message by vm.message.collectAsState()
    val imported by vm.imported.collectAsState()
    val context = LocalContext.current

    var renameTarget by remember { mutableStateOf<Session?>(null) }
    var deleteTarget by remember { mutableStateOf<Session?>(null) }
    var gpxTarget by remember { mutableStateOf<Session?>(null) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importCsv(context, uri)
    }
    val gpxLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val target = gpxTarget
        if (uri != null && target != null) vm.attachGpx(context, target.id, uri)
        gpxTarget = null
    }

    LaunchedEffect(imported) {
        imported?.let {
            vm.consumeImported()
            onOpenSession(it)
        }
    }

    Column(Modifier.fillMaxSize().padding(contentPadding)) {
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (AppConfig.Features.ENABLE_IMPORT) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppConfig.Ui.screenPadding, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(onClick = { importLauncher.launch(arrayOf("*/*")) }, modifier = Modifier.weight(1f)) {
                    LucideIcon(R.drawable.ic_upload, size = 18.dp, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.sessions_import_csv))
                }
            }
        }
        message?.let { msg ->
            Text(
                msg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = AppConfig.Ui.screenPadding),
            )
            LaunchedEffect(msg) { kotlinx.coroutines.delay(5000); vm.consumeMessage() }
        }
        if (sessions.isEmpty()) {
            EmptyState(
                icon = R.drawable.ic_history,
                title = stringResource(R.string.sessions_empty_title),
                text = stringResource(R.string.sessions_empty_text),
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = AppConfig.Ui.screenPadding, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(AppConfig.Ui.cardSpacing),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(sessions, key = { it.id }) { s ->
                    SessionCard(
                        session = s,
                        onOpen = { onOpenSession(s.id) },
                        onRename = { renameTarget = s },
                        onDelete = { deleteTarget = s },
                        onAttachGpx = { gpxTarget = s; gpxLauncher.launch(arrayOf("*/*")) },
                    )
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    renameTarget?.let { s ->
        TextInputDialog(
            title = stringResource(R.string.action_rename),
            initial = s.name,
            confirmText = stringResource(R.string.action_save),
            label = stringResource(R.string.session_name),
            onConfirm = { vm.rename(s.id, it) },
            onDismiss = { renameTarget = null },
        )
    }
    deleteTarget?.let { s ->
        ConfirmDialog(
            title = stringResource(R.string.sessions_delete_title),
            text = stringResource(R.string.sessions_delete_text, s.name),
            confirmText = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = { vm.delete(s.id) },
            onDismiss = { deleteTarget = null },
        )
    }
}

@Composable
private fun SessionCard(
    session: Session,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onAttachGpx: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    AppCard(onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LucideIcon(
                if (session.source == Session.SOURCE_IMPORTED) R.drawable.ic_file_text else R.drawable.ic_activity,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(session.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    Formatters.date(session.createdAt) + "  ·  " +
                        (if (session.durationMs > 0) Formatters.duration(session.durationMs) else Formatters.fileSize(session.fileBytes)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { menu = true }) { LucideIcon(R.drawable.ic_ellipsis_vertical) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.action_rename)) }, onClick = { menu = false; onRename() },
                    leadingIcon = { LucideIcon(R.drawable.ic_pencil, size = 18.dp) })
                DropdownMenuItem(text = { Text(stringResource(if (session.hasGpx) R.string.action_replace_gpx else R.string.action_attach_gpx)) },
                    onClick = { menu = false; onAttachGpx() }, leadingIcon = { LucideIcon(R.drawable.ic_map_pin, size = 18.dp) })
                DropdownMenuItem(text = { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) },
                    onClick = { menu = false; onDelete() }, leadingIcon = { LucideIcon(R.drawable.ic_trash_2, size = 18.dp, tint = MaterialTheme.colorScheme.error) })
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Badge(R.drawable.ic_footprints, session.lastSteps?.toString() ?: "–", highlight = session.lastSteps != null)
            if (session.lastAlgorithmId != null) Badge(R.drawable.ic_flask_conical, stringResource(AlgorithmText.shortName(session.lastAlgorithmId)))
            if (session.hasGpx) Badge(R.drawable.ic_route, if (session.gpsDistanceM > 0) Formatters.distance(session.gpsDistanceM) else "GPX")
            if (session.hwStepCounter >= 0) Badge(R.drawable.ic_smartphone, session.hwStepCounter.toString())
        }
    }
}

@Composable
private fun Badge(icon: Int, text: String, highlight: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        LucideIcon(icon, size = 14.dp, tint = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(4.dp))
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (highlight) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
