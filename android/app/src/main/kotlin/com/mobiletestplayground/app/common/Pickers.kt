package com.mobiletestplayground.app.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.mobiletestplayground.app.R
import com.mobiletestplayground.core.domain.misc.DateMath
import com.mobiletestplayground.core.ui.TestTags
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.Locale

/** Pickers start on fixed values so "open and confirm" always yields the same result. */
object PickerDefaults {
    val DEFAULT_TIME: LocalTime = LocalTime.of(9, 30)

    fun toMillis(date: LocalDate): Long = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    fun toDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

    fun formatTime(hour: Int, minute: Int): String = String.format(Locale.ROOT, "%02d:%02d", hour, minute)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaygroundDatePickerDialog(
    initialIsoDate: String?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val initial = initialIsoDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: DateMath.REFERENCE_DATE
    val state = rememberDatePickerState(initialSelectedDateMillis = PickerDefaults.toMillis(initial))
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { state.selectedDateMillis?.let { onConfirm(PickerDefaults.toDate(it).toString()) } ?: onDismiss() },
                modifier = Modifier.testTag(TestTags.DATE_PICKER_CONFIRM),
            ) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag(TestTags.DATE_PICKER_CANCEL)) {
                Text(stringResource(R.string.action_cancel))
            }
        },
        modifier = Modifier.testTag(TestTags.DATE_PICKER_DIALOG),
    ) {
        DatePicker(state = state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaygroundTimePickerDialog(
    initialTime: String?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val initial = initialTime?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: PickerDefaults.DEFAULT_TIME
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.form_pick_time)) },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(PickerDefaults.formatTime(state.hour, state.minute)) },
                modifier = Modifier.testTag(TestTags.TIME_PICKER_CONFIRM),
            ) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag(TestTags.TIME_PICKER_CANCEL)) {
                Text(stringResource(R.string.action_cancel))
            }
        },
        modifier = Modifier.testTag(TestTags.TIME_PICKER_DIALOG),
    )
}
