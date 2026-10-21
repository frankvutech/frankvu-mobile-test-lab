package com.mobiletestplayground.app.scenarios.forms

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mobiletestplayground.app.R
import com.mobiletestplayground.app.common.PlaygroundDatePickerDialog
import com.mobiletestplayground.app.common.PlaygroundTimePickerDialog
import com.mobiletestplayground.core.domain.form.FieldError
import com.mobiletestplayground.core.domain.form.FormField
import com.mobiletestplayground.core.domain.form.FormValidator
import com.mobiletestplayground.core.ui.TestTags
import com.mobiletestplayground.core.ui.components.ErrorText
import com.mobiletestplayground.core.ui.components.LabeledCheckbox
import com.mobiletestplayground.core.ui.components.LabeledSwitch
import com.mobiletestplayground.core.ui.components.PlaygroundScaffold
import com.mobiletestplayground.core.ui.components.ScreenColumn
import com.mobiletestplayground.core.ui.components.SectionTitle
import com.mobiletestplayground.core.ui.components.ValueRow

@Composable
fun FormsScreen(onBack: () -> Unit, viewModel: FormsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val input = state.input
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showTimePicker by rememberSaveable { mutableStateOf(false) }

    PlaygroundScaffold(stringResource(R.string.scenario_forms), TestTags.FORMS_SCREEN, onBack) { padding ->
        ScreenColumn(padding, Modifier.imePadding()) {
            FormTextField(
                input.fullName,
                { v -> viewModel.edit { it.copy(fullName = v) } },
                R.string.form_full_name,
                TestTags.FORM_FULL_NAME,
                state.errors[FormField.FULL_NAME],
                FormField.FULL_NAME,
            )
            FormTextField(
                input.email,
                { v -> viewModel.edit { it.copy(email = v) } },
                R.string.login_email,
                TestTags.FORM_EMAIL,
                state.errors[FormField.EMAIL],
                FormField.EMAIL,
                KeyboardType.Email,
            )
            FormTextField(
                input.password,
                { v -> viewModel.edit { it.copy(password = v) } },
                R.string.login_password,
                TestTags.FORM_PASSWORD,
                state.errors[FormField.PASSWORD],
                FormField.PASSWORD,
                KeyboardType.Password,
                secret = true,
            )
            FormTextField(
                input.age,
                { v -> viewModel.edit { it.copy(age = v) } },
                R.string.form_age,
                TestTags.FORM_AGE,
                state.errors[FormField.AGE],
                FormField.AGE,
                KeyboardType.Number,
            )
            CountryDropdown(input.country, { code -> viewModel.edit { it.copy(country = code) } })
            state.errors[FormField.COUNTRY]?.let {
                ErrorText(fieldErrorText(FormField.COUNTRY, it), TestTags.formError(TestTags.FORM_COUNTRY))
            }

            SectionTitle(R.string.form_plan)
            Column(Modifier.selectableGroup()) {
                FormsViewModel.PLANS.forEach { plan ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(selected = input.plan == plan, role = Role.RadioButton, onClick = {
                                viewModel.edit { it.copy(plan = plan) }
                            })
                            .testTag(TestTags.formPlan(plan)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = input.plan == plan, onClick = null)
                        Text(stringResource(planLabel(plan)))
                    }
                }
            }
            state.errors[FormField.PLAN]?.let { ErrorText(fieldErrorText(FormField.PLAN, it), TestTags.formError(TestTags.FORM_PLAN)) }

            LabeledSwitch(stringResource(R.string.form_newsletter), input.newsletter, { v ->
                viewModel.edit { it.copy(newsletter = v) }
            }, TestTags.FORM_NEWSLETTER)

            ValueRow(stringResource(R.string.form_satisfaction), input.satisfaction.toString(), TestTags.FORM_SATISFACTION_VALUE)
            val sliderDescription = stringResource(R.string.form_satisfaction)
            Slider(
                value = input.satisfaction.toFloat(),
                onValueChange = { v -> viewModel.edit { it.copy(satisfaction = v.toInt()) } },
                valueRange = 0f..10f,
                steps = 9,
                modifier = Modifier
                    .testTag(TestTags.FORM_SATISFACTION)
                    .semantics { contentDescription = sliderDescription },
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.testTag(TestTags.FORM_DATE)) {
                    Text(stringResource(R.string.form_pick_date))
                }
                Text(
                    input.date ?: stringResource(R.string.value_not_set),
                    modifier = Modifier
                        .weight(1f)
                        .testTag(TestTags.FORM_DATE_VALUE),
                    textAlign = TextAlign.End,
                )
            }
            state.errors[FormField.DATE]?.let { ErrorText(fieldErrorText(FormField.DATE, it), TestTags.formError(TestTags.FORM_DATE)) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { showTimePicker = true }, modifier = Modifier.testTag(TestTags.FORM_TIME)) {
                    Text(stringResource(R.string.form_pick_time))
                }
                Text(
                    input.time ?: stringResource(R.string.value_not_set),
                    modifier = Modifier
                        .weight(1f)
                        .testTag(TestTags.FORM_TIME_VALUE),
                    textAlign = TextAlign.End,
                )
            }

            OutlinedTextField(
                value = input.comments,
                onValueChange = { v -> viewModel.edit { it.copy(comments = v) } },
                label = { Text(stringResource(R.string.form_comments)) },
                minLines = 3,
                isError = state.errors[FormField.COMMENTS] != null,
                supportingText = {
                    Text(
                        stringResource(R.string.form_comments_counter, input.comments.length, FormValidator.COMMENTS_MAX),
                        modifier = Modifier.testTag(TestTags.FORM_COMMENTS_COUNTER),
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TestTags.FORM_COMMENTS),
            )
            state.errors[FormField.COMMENTS]?.let {
                ErrorText(fieldErrorText(FormField.COMMENTS, it), TestTags.formError(TestTags.FORM_COMMENTS))
            }

            LabeledCheckbox(stringResource(R.string.form_terms), input.acceptTerms, { v ->
                viewModel.edit { it.copy(acceptTerms = v) }
            }, TestTags.FORM_TERMS)
            state.errors[FormField.TERMS]?.let { ErrorText(fieldErrorText(FormField.TERMS, it), TestTags.formError(TestTags.FORM_TERMS)) }

            if (state.showErrors && state.errors.isNotEmpty()) {
                ErrorText(pluralError(state.errors.size), TestTags.FORM_ERROR_SUMMARY)
            }
            if (state.submitted) {
                Text(
                    stringResource(R.string.form_success),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag(TestTags.FORM_SUCCESS),
                )
            }
            Row {
                Button(onClick = viewModel::submit, modifier = Modifier.weight(1f).testTag(TestTags.FORM_SUBMIT)) {
                    Text(stringResource(R.string.form_submit))
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = viewModel::reset, modifier = Modifier.weight(1f).testTag(TestTags.FORM_RESET)) {
                    Text(stringResource(R.string.form_reset))
                }
            }
        }
    }

    if (showDatePicker) {
        PlaygroundDatePickerDialog(
            initialIsoDate = input.date,
            onConfirm = { iso ->
                viewModel.edit { it.copy(date = iso) }
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false },
        )
    }
    if (showTimePicker) {
        PlaygroundTimePickerDialog(
            initialTime = input.time,
            onConfirm = { time ->
                viewModel.edit { it.copy(time = time) }
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false },
        )
    }
}

@Composable
private fun FormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: Int,
    tag: String,
    error: FieldError?,
    field: FormField,
    keyboardType: KeyboardType = KeyboardType.Text,
    secret: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(label)) },
        singleLine = true,
        isError = error != null,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag),
    )
    error?.let { ErrorText(fieldErrorText(field, it), TestTags.formError(tag)) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CountryDropdown(selected: String?, onSelect: (String) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected?.let { stringResource(countryLabel(it)) } ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.form_country)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
                .testTag(TestTags.FORM_COUNTRY),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            FormsViewModel.COUNTRIES.forEach { code ->
                DropdownMenuItem(
                    text = { Text(stringResource(countryLabel(code))) },
                    onClick = {
                        onSelect(code)
                        expanded = false
                    },
                    modifier = Modifier.testTag(TestTags.formCountryOption(code)),
                )
            }
        }
    }
}

private fun countryLabel(code: String) = when (code) {
    "de" -> R.string.country_de
    "fr" -> R.string.country_fr
    "gb" -> R.string.country_gb
    else -> R.string.country_us
}

private fun planLabel(plan: String) = when (plan) {
    "basic" -> R.string.plan_basic
    "pro" -> R.string.plan_pro
    else -> R.string.plan_enterprise
}

@Composable
private fun pluralError(count: Int) = androidx.compose.ui.res.pluralStringResource(R.plurals.form_error_summary, count, count)

@Composable
fun fieldErrorText(field: FormField, error: FieldError): String = when (error) {
    FieldError.REQUIRED -> stringResource(R.string.error_required)
    FieldError.INVALID_EMAIL -> stringResource(R.string.login_error_invalid_email)
    FieldError.TOO_SHORT -> stringResource(
        R.string.error_too_short,
        if (field == FormField.PASSWORD) FormValidator.PASSWORD_MIN else FormValidator.NAME_MIN,
    )
    FieldError.TOO_LONG -> stringResource(
        R.string.error_too_long,
        when (field) {
            FormField.PASSWORD -> FormValidator.PASSWORD_MAX
            FormField.COMMENTS -> FormValidator.COMMENTS_MAX
            else -> FormValidator.NAME_MAX
        },
    )
    FieldError.NOT_A_NUMBER -> stringResource(R.string.error_not_a_number)
    FieldError.OUT_OF_RANGE -> stringResource(R.string.error_out_of_range, FormValidator.AGE_MIN, FormValidator.AGE_MAX)
    FieldError.MUST_ACCEPT -> stringResource(R.string.error_must_accept)
}
