package com.mobiletestplayground.app.scenarios.datetime

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mobiletestplayground.app.R
import com.mobiletestplayground.app.common.PlaygroundDatePickerDialog
import com.mobiletestplayground.app.common.PlaygroundTimePickerDialog
import com.mobiletestplayground.core.domain.misc.DateMath
import com.mobiletestplayground.core.ui.TestTags
import com.mobiletestplayground.core.ui.components.PlaygroundScaffold
import com.mobiletestplayground.core.ui.components.ScreenColumn
import com.mobiletestplayground.core.ui.components.ValueRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate
import javax.inject.Inject

/**
 * Dates are compared with the fixed reference date (2025-01-15), never the device clock, so
 * "days from reference" is the same on every run. Values are ISO-8601 for locale independence.
 */
@HiltViewModel
class DateTimeViewModel @Inject constructor(private val handle: SavedStateHandle) : ViewModel() {
    val date: StateFlow<String?> = handle.getStateFlow("date", null)
    val time: StateFlow<String?> = handle.getStateFlow("time", null)

    fun setDate(iso: String) {
        handle["date"] = iso
    }

    fun setTime(value: String) {
        handle["time"] = value
    }

    fun daysFromReference(iso: String): Long = DateMath.daysFromReference(LocalDate.parse(iso))
}

@Composable
fun DateTimeScreen(onBack: () -> Unit, viewModel: DateTimeViewModel = hiltViewModel()) {
    val date by viewModel.date.collectAsStateWithLifecycle()
    val time by viewModel.time.collectAsStateWithLifecycle()
    var showDate by rememberSaveable { mutableStateOf(false) }
    var showTime by rememberSaveable { mutableStateOf(false) }
    val notSet = stringResource(R.string.value_not_set)

    PlaygroundScaffold(stringResource(R.string.scenario_date_time), TestTags.DATE_TIME_SCREEN, onBack) { padding ->
        ScreenColumn(padding) {
            ValueRow(stringResource(R.string.date_time_reference), DateMath.REFERENCE_DATE.toString(), TestTags.DATE_TIME_REFERENCE)
            OutlinedButton(onClick = { showDate = true }, modifier = Modifier.fillMaxWidth().testTag(TestTags.DATE_TIME_PICK_DATE)) {
                Text(stringResource(R.string.form_pick_date))
            }
            ValueRow(stringResource(R.string.date_time_selected_date), date ?: notSet, TestTags.DATE_TIME_DATE_VALUE)
            val days = date?.let { viewModel.daysFromReference(it) }
            ValueRow(
                stringResource(R.string.date_time_days_from_reference),
                days?.let { pluralStringResource(R.plurals.date_time_days, it.toInt(), it.toInt()) } ?: notSet,
                TestTags.DATE_TIME_DAYS_FROM_REFERENCE,
            )
            OutlinedButton(onClick = { showTime = true }, modifier = Modifier.fillMaxWidth().testTag(TestTags.DATE_TIME_PICK_TIME)) {
                Text(stringResource(R.string.form_pick_time))
            }
            ValueRow(stringResource(R.string.date_time_selected_time), time ?: notSet, TestTags.DATE_TIME_TIME_VALUE)
        }
    }

    if (showDate) {
        PlaygroundDatePickerDialog(date, onConfirm = {
            viewModel.setDate(it)
            showDate = false
        }, onDismiss = { showDate = false })
    }
    if (showTime) {
        PlaygroundTimePickerDialog(time, onConfirm = {
            viewModel.setTime(it)
            showTime = false
        }, onDismiss = { showTime = false })
    }
}
