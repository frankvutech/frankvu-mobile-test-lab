package com.mobiletestplayground.app.scenarios.auth

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mobiletestplayground.app.R
import com.mobiletestplayground.core.domain.auth.AuthRepository
import com.mobiletestplayground.core.domain.model.Session
import com.mobiletestplayground.core.ui.TestTags
import com.mobiletestplayground.core.ui.components.PlaygroundScaffold
import com.mobiletestplayground.core.ui.components.ScreenColumn
import com.mobiletestplayground.core.ui.components.SectionTitle
import com.mobiletestplayground.core.ui.components.ValueRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class AuthScenarioViewModel @Inject constructor(authRepository: AuthRepository) : ViewModel() {
    val session: StateFlow<Session?> = authRepository.session
}

/** Shows the current session and the authorization outcome of opening the admin area. */
@Composable
fun AuthScenarioScreen(
    onBack: () -> Unit,
    onOpenAdmin: () -> Unit,
    onLogout: () -> Unit,
    viewModel: AuthScenarioViewModel = hiltViewModel(),
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val notSet = stringResource(R.string.value_none)
    PlaygroundScaffold(stringResource(R.string.scenario_auth), TestTags.AUTH_SCREEN, onBack) { padding ->
        ScreenColumn(padding) {
            ValueRow(
                stringResource(R.string.auth_status),
                stringResource(if (session != null) R.string.auth_signed_in else R.string.auth_signed_out),
                TestTags.AUTH_STATUS,
            )
            ValueRow(stringResource(R.string.login_email), session?.user?.email ?: notSet, TestTags.AUTH_USER_EMAIL)
            ValueRow(stringResource(R.string.profile_role), session?.user?.role?.name ?: notSet, TestTags.AUTH_USER_ROLE)
            ValueRow(
                stringResource(R.string.login_remember_me),
                stringResource(if (session?.rememberMe == true) R.string.value_on else R.string.value_off),
                TestTags.AUTH_REMEMBER_STATE,
            )
            SectionTitle(stringResource(R.string.auth_authorization))
            Text(stringResource(R.string.auth_authorization_hint), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = onOpenAdmin, modifier = Modifier.fillMaxWidth().testTag(TestTags.AUTH_OPEN_ADMIN)) {
                Text(stringResource(R.string.admin_title))
            }
            if (session != null) {
                Button(onClick = onLogout, modifier = Modifier.fillMaxWidth().testTag(TestTags.AUTH_LOGOUT)) {
                    Text(stringResource(R.string.profile_logout))
                }
            }
        }
    }
}
