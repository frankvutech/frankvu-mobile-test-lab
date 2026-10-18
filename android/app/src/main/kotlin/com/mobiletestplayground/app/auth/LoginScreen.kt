package com.mobiletestplayground.app.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mobiletestplayground.app.R
import com.mobiletestplayground.core.domain.auth.LoginError
import com.mobiletestplayground.core.ui.TestTags
import com.mobiletestplayground.core.ui.components.ErrorText
import com.mobiletestplayground.core.ui.components.LabeledCheckbox
import com.mobiletestplayground.core.ui.components.PlaygroundScaffold

@Composable
fun LoginScreen(
    redirect: String?,
    onLoggedIn: (redirect: String?) -> Unit,
    onRegister: () -> Unit,
    onForgotPassword: () -> Unit,
    onSettings: () -> Unit,
    onContinueAsGuest: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val authenticationRequired by viewModel.authenticationRequired.collectAsStateWithLifecycle()
    LaunchedEffect(state.loggedIn) {
        if (state.loggedIn) {
            viewModel.onNavigated()
            onLoggedIn(redirect)
        }
    }
    PlaygroundScaffold(
        title = stringResource(R.string.login_title),
        rootTag = TestTags.LOGIN_SCREEN,
        onBack = null,
        actions = {
            IconButton(onClick = onSettings, modifier = Modifier.testTag(TestTags.LOGIN_SETTINGS)) {
                Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings_title))
            }
        },
    ) { padding ->
        LoginContent(
            state = state,
            showRedirectMessage = redirect != null,
            onEmailChange = viewModel::onEmailChange,
            onPasswordChange = viewModel::onPasswordChange,
            onTogglePassword = viewModel::onTogglePasswordVisibility,
            onRememberMeChange = viewModel::onRememberMeChange,
            onSubmit = viewModel::submit,
            onRegister = onRegister,
            onForgotPassword = onForgotPassword,
            onContinueAsGuest = onContinueAsGuest.takeUnless { authenticationRequired },
            modifier = Modifier.padding(padding),
        )
    }
}

@Composable
private fun LoginContent(
    state: LoginUiState,
    showRedirectMessage: Boolean,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTogglePassword: () -> Unit,
    onRememberMeChange: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    onRegister: () -> Unit,
    onForgotPassword: () -> Unit,
    onContinueAsGuest: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.login_heading), style = MaterialTheme.typography.headlineSmall)
        if (showRedirectMessage) {
            Text(
                stringResource(R.string.login_redirect_message),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag(TestTags.LOGIN_REDIRECT_MESSAGE),
            )
        }
        OutlinedTextField(
            value = state.email,
            onValueChange = onEmailChange,
            label = { Text(stringResource(R.string.login_email)) },
            singleLine = true,
            enabled = !state.isLoading,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TestTags.LOGIN_EMAIL),
        )
        val toggleDescription = stringResource(if (state.passwordVisible) R.string.login_hide_password else R.string.login_show_password)
        OutlinedTextField(
            value = state.password,
            onValueChange = onPasswordChange,
            label = { Text(stringResource(R.string.login_password)) },
            singleLine = true,
            enabled = !state.isLoading,
            visualTransformation = if (state.passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            trailingIcon = {
                IconButton(
                    onClick = onTogglePassword,
                    modifier = Modifier
                        .testTag(TestTags.LOGIN_PASSWORD_TOGGLE)
                        .semantics { contentDescription = toggleDescription },
                ) {
                    Icon(if (state.passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, contentDescription = null)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TestTags.LOGIN_PASSWORD),
        )
        LabeledCheckbox(
            label = stringResource(R.string.login_remember_me),
            checked = state.rememberMe,
            onCheckedChange = onRememberMeChange,
            tag = TestTags.LOGIN_REMEMBER_ME,
        )
        state.error?.let { ErrorText(stringResource(it.message()), TestTags.LOGIN_ERROR) }
        Button(
            onClick = onSubmit,
            enabled = !state.isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TestTags.LOGIN_BUTTON),
        ) {
            if (state.isLoading) {
                val loading = stringResource(R.string.login_loading)
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier
                        .size(20.dp)
                        .testTag(TestTags.LOGIN_LOADING)
                        .semantics { contentDescription = loading },
                )
            } else {
                Text(stringResource(R.string.login_button))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onForgotPassword, modifier = Modifier.testTag(TestTags.LOGIN_FORGOT_PASSWORD)) {
                Text(stringResource(R.string.login_forgot_password))
            }
            TextButton(onClick = onRegister, modifier = Modifier.testTag(TestTags.LOGIN_REGISTER)) {
                Text(stringResource(R.string.login_register))
            }
        }
        if (onContinueAsGuest != null) {
            OutlinedButton(
                onClick = onContinueAsGuest,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TestTags.LOGIN_CONTINUE_AS_GUEST),
            ) { Text(stringResource(R.string.login_continue_as_guest)) }
        }
        Text(stringResource(R.string.login_hint), style = MaterialTheme.typography.bodySmall)
    }
}

fun LoginError.message(): Int = when (this) {
    LoginError.EMPTY_EMAIL -> R.string.login_error_empty_email
    LoginError.INVALID_EMAIL -> R.string.login_error_invalid_email
    LoginError.EMPTY_PASSWORD -> R.string.login_error_empty_password
    LoginError.UNKNOWN_USER -> R.string.login_error_unknown_user
    LoginError.INCORRECT_PASSWORD -> R.string.login_error_incorrect_password
    LoginError.ACCOUNT_LOCKED -> R.string.login_error_locked
    LoginError.SERVICE_UNAVAILABLE -> R.string.data_error_unavailable
}
