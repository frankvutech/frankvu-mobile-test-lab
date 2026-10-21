package com.mobiletestplayground.app.scenarios.filepicker

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mobiletestplayground.app.R
import com.mobiletestplayground.core.ui.TestTags
import com.mobiletestplayground.core.ui.components.PlaygroundScaffold
import com.mobiletestplayground.core.ui.components.ScreenColumn
import com.mobiletestplayground.core.ui.components.ValueRow
import com.mobiletestplayground.core.ui.components.textTag
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Metadata of a picked file. Only metadata is read; the file content is never loaded. */
data class PickedFile(val name: String, val mimeType: String, val sizeBytes: Long)

fun ContentResolver.describe(uri: Uri): PickedFile {
    var name = uri.lastPathSegment.orEmpty()
    var size = -1L
    query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            name = cursor.getString(0) ?: name
            if (!cursor.isNull(1)) size = cursor.getLong(1)
        }
    }
    return PickedFile(name, getType(uri) ?: "application/octet-stream", size)
}

@HiltViewModel
class FilePickerViewModel @Inject constructor(private val handle: SavedStateHandle) : ViewModel() {
    val name: StateFlow<String?> = handle.getStateFlow("name", null)
    val type: StateFlow<String?> = handle.getStateFlow("type", null)
    val size: StateFlow<Long> = handle.getStateFlow("size", -1L)

    fun onPicked(file: PickedFile) {
        handle["name"] = file.name
        handle["type"] = file.mimeType
        handle["size"] = file.sizeBytes
    }

    fun clear() {
        listOf("name", "type", "size").forEach { handle.remove<Any>(it) }
    }
}

/**
 * Uses the system Photo Picker and the Storage Access Framework, which need no runtime
 * permission. Pickers are system UI: drive them with UIAutomator, not Compose tests.
 */
@Composable
fun FilePickerScreen(onBack: () -> Unit, viewModel: FilePickerViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val name by viewModel.name.collectAsStateWithLifecycle()
    val type by viewModel.type.collectAsStateWithLifecycle()
    val size by viewModel.size.collectAsStateWithLifecycle()
    val onUri: (Uri?) -> Unit = { uri -> uri?.let { viewModel.onPicked(context.contentResolver.describe(it)) } }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia(), onUri)
    val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), onUri)

    PlaygroundScaffold(stringResource(R.string.scenario_file_picker), TestTags.FILE_PICKER_SCREEN, onBack) { padding ->
        ScreenColumn(padding) {
            Text(stringResource(R.string.file_picker_intro), modifier = Modifier.testTag(textTag(R.string.file_picker_intro)))
            Button(
                onClick = { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                modifier = Modifier.fillMaxWidth().testTag(TestTags.FILE_PICKER_PICK_IMAGE),
            ) { Text(stringResource(R.string.file_picker_image)) }
            OutlinedButton(
                onClick = { documentPicker.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth().testTag(TestTags.FILE_PICKER_PICK_DOCUMENT),
            ) { Text(stringResource(R.string.file_picker_document)) }
            val picked = name
            if (picked == null) {
                Text(stringResource(R.string.file_picker_empty), modifier = Modifier.testTag(TestTags.FILE_PICKER_EMPTY))
            } else {
                ValueRow(stringResource(R.string.file_picker_name), picked, TestTags.FILE_PICKER_RESULT_NAME)
                ValueRow(stringResource(R.string.file_picker_type), type.orEmpty(), TestTags.FILE_PICKER_RESULT_TYPE)
                ValueRow(
                    stringResource(R.string.file_picker_size),
                    if (size >= 0) size.toString() else stringResource(R.string.value_unknown),
                    TestTags.FILE_PICKER_RESULT_SIZE,
                )
                OutlinedButton(onClick = viewModel::clear, modifier = Modifier.testTag(TestTags.FILE_PICKER_CLEAR)) {
                    Text(stringResource(R.string.action_clear))
                }
            }
        }
    }
}
