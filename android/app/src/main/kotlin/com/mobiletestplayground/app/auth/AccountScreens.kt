package com.mobiletestplayground.app.auth

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mobiletestplayground.app.R
import com.mobiletestplayground.core.domain.auth.RegisterError
import com.mobiletestplayground.core.domain.auth.ResetPasswordError
import com.mobiletestplayground.core.domain.validation.PasswordPolicy
import com.mobiletestplayground.core.ui.TestTags
import com.mobiletestplayground.core.ui.components.ErrorText
import com.mobiletestplayground.core.ui.components.PlaygroundScaffold
import com.mobiletestplayground.core.ui.components.ScreenColumn
import com.mobiletestplayground.core.ui.components.textTag

@Composable
private fun TextInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: Int,
    tag: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    secret: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(label)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag),
    )
}

@Composable
fun RegisterScreen(onBack: () -> Unit, onRegistered: () -> Unit, viewModel: RegisterViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PlaygroundScaffold(stringResource(R.string.register_title), TestTags.REGISTER_SCREEN, onBack) { padding ->
        ScreenColumn(padding, Modifier.imePadding()) {
            if (state.registered) {
                Text(
                    stringResource(R.string.register_success),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag(TestTags.REGISTER_SUCCESS),
                )
                Button(onClick = onRegistered, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.register_back_to_login)) }
                return@ScreenColumn
            }
            TextInput(state.name, viewModel::onNameChange, R.string.register_name, TestTags.REGISTER_NAME)
            TextInput(state.email, viewModel::onEmailChange, R.string.login_email, TestTags.REGISTER_EMAIL, KeyboardType.Email)
            TextInput(state.password, viewModel::onPasswordChange, R.string.login_password, TestTags.REGISTER_PASSWORD, secret = true)
            TextInput(
                state.confirmPassword,
                viewModel::onConfirmChange,
                R.string.register_confirm_password,
                TestTags.REGISTER_CONFIRM_PASSWORD,
                secret = true,
            )
            Text(
                stringResource(R.string.password_rules),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag(textTag(R.string.password_rules)),
            )
            state.error?.let { ErrorText(it.text(), TestTags.REGISTER_ERROR) }
            Button(
                onClick = viewModel::submit,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TestTags.REGISTER_BUTTON),
            ) { Text(stringResource(R.string.register_button)) }
        }
    }
}

@Composable
fun ForgotPasswordScreen(onBack: () -> Unit, onContinue: (String) -> Unit, viewModel: ForgotPasswordViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PlaygroundScaffold(stringResource(R.string.forgot_title), TestTags.FORGOT_SCREEN, onBack) { padding ->
        ScreenColumn(padding, Modifier.imePadding()) {
            Text(stringResource(R.string.forgot_instructions), modifier = Modifier.testTag(textTag(R.string.forgot_instructions)))
            TextInput(state.email, viewModel::onEmailChange, R.string.login_email, TestTags.FORGOT_EMAIL, KeyboardType.Email)
            if (state.invalidEmail) ErrorText(stringResource(R.string.login_error_invalid_email), TestTags.FORGOT_ERROR)
            if (state.submitted) {
                Text(
                    stringResource(R.string.forgot_confirmation),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag(TestTags.FORGOT_CONFIRMATION),
                )
                Button(
                    onClick = { onContinue(state.email.trim()) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TestTags.FORGOT_CONTINUE),
                ) { Text(stringResource(R.string.forgot_continue)) }
            } else {
                Button(
                    onClick = viewModel::submit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TestTags.FORGOT_SUBMIT),
                ) { Text(stringResource(R.string.forgot_submit)) }
            }
        }
    }
}

@Composable
fun ResetPasswordScreen(
    initialEmail: String,
    onBack: () -> Unit,
    onBackToLogin: () -> Unit,
    viewModel: ResetPasswordViewModel = hiltViewModel(),
) {
    LaunchedEffect(initialEmail) { viewModel.prefill(initialEmail) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    PlaygroundScaffold(stringResource(R.string.reset_title), TestTags.RESET_SCREEN, onBack) { padding ->
        ScreenColumn(padding, Modifier.imePadding()) {
            if (state.done) {
                Text(
                    stringResource(R.string.reset_success),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag(TestTags.RESET_SUCCESS),
                )
                Button(
                    onClick = onBackToLogin,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TestTags.RESET_BACK_TO_LOGIN),
                ) { Text(stringResource(R.string.register_back_to_login)) }
                return@ScreenColumn
            }
            Text(stringResource(R.string.reset_instructions), modifier = Modifier.testTag(textTag(R.string.reset_instructions)))
            TextInput(state.email, viewModel::onEmailChange, R.string.login_email, TestTags.RESET_EMAIL, KeyboardType.Email)
            TextInput(state.code, viewModel::onCodeChange, R.string.reset_code, TestTags.RESET_CODE, KeyboardType.Number)
            TextInput(state.password, viewModel::onPasswordChange, R.string.reset_new_password, TestTags.RESET_PASSWORD, secret = true)
            TextInput(
                state.confirmPassword,
                viewModel::onConfirmChange,
                R.string.register_confirm_password,
                TestTags.RESET_CONFIRM_PASSWORD,
                secret = true,
            )
            state.error?.let { ErrorText(it.text(), TestTags.RESET_ERROR) }
            Button(
                onClick = viewModel::submit,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TestTags.RESET_SUBMIT),
            ) { Text(stringResource(R.string.reset_submit)) }
        }
    }
}

@Composable
private fun PasswordPolicy.Violation.text(): String = stringResource(
    when (this) {
        PasswordPolicy.Violation.TOO_SHORT -> R.string.password_too_short
        PasswordPolicy.Violation.TOO_LONG -> R.string.password_too_long
        PasswordPolicy.Violation.MISSING_UPPERCASE -> R.string.password_missing_uppercase
        PasswordPolicy.Violation.MISSING_DIGIT -> R.string.password_missing_digit
        PasswordPolicy.Violation.MISSING_SPECIAL -> R.string.password_missing_special
    },
)

@Composable
private fun RegisterError.text(): String = when (this) {
    RegisterError.EmptyName -> stringResource(R.string.register_error_empty_name)
    RegisterError.EmptyEmail -> stringResource(R.string.login_error_empty_email)
    RegisterError.InvalidEmail -> stringResource(R.string.login_error_invalid_email)
    is RegisterError.WeakPassword -> violation.text()
    RegisterError.PasswordsDoNotMatch -> stringResource(R.string.password_mismatch)
    RegisterError.EmailAlreadyRegistered -> stringResource(R.string.register_error_email_taken)
    RegisterError.ServiceUnavailable -> stringResource(R.string.data_error_unavailable)
}

@Composable
private fun ResetPasswordError.text(): String = when (this) {
    ResetPasswordError.InvalidEmail -> stringResource(R.string.login_error_invalid_email)
    ResetPasswordError.InvalidCode -> stringResource(R.string.reset_error_code)
    is ResetPasswordError.WeakPassword -> violation.text()
    ResetPasswordError.PasswordsDoNotMatch -> stringResource(R.string.password_mismatch)
    ResetPasswordError.UnknownUser -> stringResource(R.string.login_error_unknown_user)
    ResetPasswordError.ServiceUnavailable -> stringResource(R.string.data_error_unavailable)
}
