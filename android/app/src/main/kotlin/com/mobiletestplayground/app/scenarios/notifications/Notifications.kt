package com.mobiletestplayground.app.scenarios.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mobiletestplayground.app.MainActivity
import com.mobiletestplayground.app.R
import com.mobiletestplayground.app.UiTiming
import com.mobiletestplayground.app.scenarios.permissions.PlaygroundPermission
import com.mobiletestplayground.app.scenarios.permissions.label
import com.mobiletestplayground.app.scenarios.permissions.resolveStatus
import com.mobiletestplayground.core.domain.misc.PermissionStatus
import com.mobiletestplayground.core.ui.TestTags
import com.mobiletestplayground.core.ui.components.PlaygroundScaffold
import com.mobiletestplayground.core.ui.components.ScreenColumn
import com.mobiletestplayground.core.ui.components.ValueRow
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

object NotificationChannels {
    const val GENERAL = "playground_general"

    fun register(context: Context) {
        val channel =
            NotificationChannel(GENERAL, context.getString(R.string.notifications_channel), NotificationManager.IMPORTANCE_DEFAULT)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}

enum class NotificationStatus { IDLE, SENT, SCHEDULED, BLOCKED }

/**
 * Posts local notifications. Tapping one opens `mtp://playground/scenario/notifications`.
 * Scheduled notifications use an app-scoped coroutine: they fire while the process is alive,
 * which is enough for deterministic UI tests (a production app would use WorkManager/AlarmManager).
 */
@Singleton
class LocalNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val timing: UiTiming,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutableStatus = MutableStateFlow(NotificationStatus.IDLE)
    val status: StateFlow<NotificationStatus> = mutableStatus.asStateFlow()

    private fun canPost(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun sendNow() {
        mutableStatus.value = if (post()) NotificationStatus.SENT else NotificationStatus.BLOCKED
    }

    fun schedule() {
        if (!canPost()) {
            mutableStatus.value = NotificationStatus.BLOCKED
            return
        }
        mutableStatus.value = NotificationStatus.SCHEDULED
        scope.launch {
            delay(timing.notificationDelayMillis)
            mutableStatus.value = if (post()) NotificationStatus.SENT else NotificationStatus.BLOCKED
        }
    }

    private fun post(): Boolean {
        if (!canPost()) return false
        val intent = Intent(Intent.ACTION_VIEW, "mtp://playground/scenario/notifications".toUri(), context, MainActivity::class.java)
        val pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, NotificationChannels.GENERAL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notifications_title))
            .setContentText(context.getString(R.string.notifications_body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        return try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            true
        } catch (e: SecurityException) {
            false
        }
    }

    private companion object {
        const val NOTIFICATION_ID = 1001
    }
}

@HiltViewModel
class NotificationsViewModel @Inject constructor(private val notifier: LocalNotifier) : ViewModel() {
    val status: StateFlow<NotificationStatus> = notifier.status
    fun sendNow() = notifier.sendNow()
    fun schedule() = notifier.schedule()
}

@Composable
fun NotificationsScreen(onBack: () -> Unit, viewModel: NotificationsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val status by viewModel.status.collectAsStateWithLifecycle()
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }
    val permission = remember(refresh) { resolveStatus(context, PlaygroundPermission.NOTIFICATIONS) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }

    PlaygroundScaffold(stringResource(R.string.scenario_notifications), TestTags.NOTIFICATIONS_SCREEN, onBack) { padding ->
        ScreenColumn(padding) {
            Text(stringResource(R.string.notifications_intro))
            ValueRow(
                stringResource(R.string.permission_notifications),
                stringResource(permission.label()),
                TestTags.NOTIFICATIONS_PERMISSION_STATUS,
            )
            if (permission != PermissionStatus.GRANTED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                OutlinedButton(
                    onClick = {
                        com.mobiletestplayground.app.scenarios.permissions.PermissionHistory.markRequested(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS,
                        )
                        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    },
                    modifier = Modifier.fillMaxWidth().testTag(TestTags.NOTIFICATIONS_REQUEST_PERMISSION),
                ) { Text(stringResource(R.string.permissions_request)) }
            }
            Button(onClick = viewModel::sendNow, modifier = Modifier.fillMaxWidth().testTag(TestTags.NOTIFICATIONS_SEND_NOW)) {
                Text(stringResource(R.string.notifications_send_now))
            }
            OutlinedButton(onClick = viewModel::schedule, modifier = Modifier.fillMaxWidth().testTag(TestTags.NOTIFICATIONS_SCHEDULE)) {
                Text(stringResource(R.string.notifications_schedule))
            }
            ValueRow(stringResource(R.string.notifications_status), stringResource(status.label()), TestTags.NOTIFICATIONS_STATUS)
        }
    }
}

private fun NotificationStatus.label() = when (this) {
    NotificationStatus.IDLE -> R.string.notifications_status_idle
    NotificationStatus.SENT -> R.string.notifications_status_sent
    NotificationStatus.SCHEDULED -> R.string.notifications_status_scheduled
    NotificationStatus.BLOCKED -> R.string.notifications_status_blocked
}
