package com.mobiletestplayground.app.scenarios.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mobiletestplayground.app.R
import com.mobiletestplayground.core.ui.TestTags
import com.mobiletestplayground.core.ui.components.PlaygroundScaffold
import com.mobiletestplayground.core.ui.components.ScreenColumn
import com.mobiletestplayground.core.ui.components.ValueRow
import com.mobiletestplayground.core.ui.components.rememberSnackbarHost
import kotlinx.coroutines.launch

/**
 * Which transient surface is open. Results are shown as stable codes (e.g. `confirmed`) so tests
 * can assert them in any locale.
 * Saved, so an open dialog survives rotation.
 */
private enum class DialogSurface { NONE, CONFIRMATION, INFORMATION, WARNING, ERROR, ACTION_SHEET, BOTTOM_SHEET }

private val actionSheetOptions = listOf("share", "duplicate", "archive")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialogsScreen(onBack: () -> Unit) {
    var open by rememberSaveable { mutableStateOf(DialogSurface.NONE) }
    var result by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbarHost = rememberSnackbarHost()
    val scope = rememberCoroutineScope()
    val snackbarMessage = stringResource(R.string.dialogs_snackbar_message)
    val undo = stringResource(R.string.action_undo)
    val close: () -> Unit = { open = DialogSurface.NONE }

    PlaygroundScaffold(
        stringResource(R.string.scenario_dialogs),
        TestTags.DIALOGS_SCREEN,
        onBack,
        snackbarHostState = snackbarHost,
    ) { padding ->
        ScreenColumn(padding) {
            ValueRow(stringResource(R.string.dialogs_result), result ?: stringResource(R.string.value_none), TestTags.DIALOGS_RESULT)
            OpenButton(R.string.dialogs_confirmation, TestTags.DIALOGS_OPEN_CONFIRMATION) { open = DialogSurface.CONFIRMATION }
            OpenButton(R.string.dialogs_information, TestTags.DIALOGS_OPEN_INFORMATION) { open = DialogSurface.INFORMATION }
            OpenButton(R.string.dialogs_warning, TestTags.DIALOGS_OPEN_WARNING) { open = DialogSurface.WARNING }
            OpenButton(R.string.dialogs_error, TestTags.DIALOGS_OPEN_ERROR) { open = DialogSurface.ERROR }
            OpenButton(R.string.dialogs_action_sheet, TestTags.DIALOGS_OPEN_ACTION_SHEET) { open = DialogSurface.ACTION_SHEET }
            OpenButton(R.string.dialogs_bottom_sheet, TestTags.DIALOGS_OPEN_BOTTOM_SHEET) { open = DialogSurface.BOTTOM_SHEET }
            Button(
                onClick = {
                    scope.launch {
                        val outcome = snackbarHost.showSnackbar(snackbarMessage, actionLabel = undo, duration = SnackbarDuration.Long)
                        result = if (outcome == SnackbarResult.ActionPerformed) "snackbar_undo" else "snackbar_dismissed"
                    }
                },
                modifier = Modifier.fillMaxWidth().testTag(TestTags.DIALOGS_SHOW_SNACKBAR),
            ) { Text(stringResource(R.string.dialogs_snackbar)) }
        }
    }

    when (open) {
        DialogSurface.CONFIRMATION -> AlertDialog(
            onDismissRequest = close,
            title = { Text(stringResource(R.string.dialog_delete_title)) },
            text = { Text(stringResource(R.string.dialog_delete_message, "Item 001")) },
            confirmButton = {
                TextButton(
                    onClick = {
                        result = "confirmed"
                        close()
                    },
                    modifier = Modifier.testTag(TestTags.DIALOG_CONFIRM),
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        result = "cancelled"
                        close()
                    },
                    modifier = Modifier.testTag(TestTags.DIALOG_CANCEL),
                ) { Text(stringResource(R.string.action_cancel)) }
            },
            modifier = Modifier.testTag(TestTags.DIALOG_CONFIRMATION),
        )
        DialogSurface.INFORMATION -> SingleActionDialog(
            Icons.Filled.Info,
            R.string.dialogs_information,
            R.string.dialogs_information_body,
            TestTags.DIALOG_INFORMATION,
        ) {
            result = "information_ok"
            close()
        }
        DialogSurface.WARNING -> SingleActionDialog(
            Icons.Filled.Warning,
            R.string.dialogs_warning,
            R.string.dialogs_warning_body,
            TestTags.DIALOG_WARNING,
        ) {
            result = "warning_ok"
            close()
        }
        DialogSurface.ERROR -> SingleActionDialog(
            Icons.Filled.Error,
            R.string.dialogs_error,
            R.string.dialogs_error_body,
            TestTags.DIALOG_ERROR,
        ) {
            result = "error_ok"
            close()
        }
        DialogSurface.ACTION_SHEET -> ModalBottomSheet(
            onDismissRequest = close,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            modifier = Modifier.testTag(TestTags.ACTION_SHEET),
        ) {
            Column(Modifier.padding(bottom = 24.dp)) {
                Text(
                    stringResource(R.string.dialogs_action_sheet),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp),
                )
                actionSheetOptions.forEach { option ->
                    ListItem(
                        headlineContent = { Text(stringResource(actionLabel(option))) },
                        modifier = Modifier
                            .testTag(TestTags.actionSheetOption(option))
                            .clickable {
                                result = "action_$option"
                                close()
                            },
                    )
                }
                TextButton(
                    onClick = {
                        result = "action_cancelled"
                        close()
                    },
                    modifier = Modifier.fillMaxWidth().testTag(TestTags.ACTION_SHEET_CANCEL),
                ) { Text(stringResource(R.string.action_cancel)) }
            }
        }
        DialogSurface.BOTTOM_SHEET -> ModalBottomSheet(
            onDismissRequest = close,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            modifier = Modifier.testTag(TestTags.BOTTOM_SHEET),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.dialogs_bottom_sheet), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.dialogs_bottom_sheet_body), modifier = Modifier.padding(vertical = 12.dp))
                Button(
                    onClick = {
                        result = "bottom_sheet_closed"
                        close()
                    },
                    modifier = Modifier.fillMaxWidth().testTag(TestTags.BOTTOM_SHEET_CLOSE),
                ) { Text(stringResource(R.string.action_close)) }
            }
        }
        DialogSurface.NONE -> Unit
    }
}

@Composable
private fun OpenButton(label: Int, tag: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().testTag(tag)) { Text(stringResource(label)) }
}

@Composable
private fun SingleActionDialog(icon: ImageVector, title: Int, body: Int, tag: String, onOk: () -> Unit) {
    AlertDialog(
        onDismissRequest = onOk,
        icon = { Icon(icon, contentDescription = null) },
        title = { Text(stringResource(title)) },
        text = { Text(stringResource(body)) },
        confirmButton = {
            TextButton(onClick = onOk, modifier = Modifier.testTag(TestTags.DIALOG_OK)) { Text(stringResource(R.string.action_ok)) }
        },
        modifier = Modifier.testTag(tag),
    )
}

private fun actionLabel(option: String) = when (option) {
    "share" -> R.string.dialogs_action_share
    "duplicate" -> R.string.dialogs_action_duplicate
    else -> R.string.dialogs_action_archive
}
