package com.mobiletestplayground.app.scenarios.permissions

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.mobiletestplayground.app.R
import com.mobiletestplayground.core.domain.misc.PermissionStatus
import com.mobiletestplayground.core.domain.misc.PermissionStatusResolver
import com.mobiletestplayground.core.ui.TestTags
import com.mobiletestplayground.core.ui.components.PlaygroundScaffold
import com.mobiletestplayground.core.ui.components.ScreenColumn
import com.mobiletestplayground.core.ui.components.textTag

/** A runtime permission the scenario can request. [id] is used in `permission_<id>_*` tags. */
enum class PlaygroundPermission(val id: String, val label: Int) {
    CAMERA("camera", R.string.permission_camera),
    PHOTOS("photos", R.string.permission_photos),
    NOTIFICATIONS("notifications", R.string.permission_notifications),
    LOCATION("location", R.string.permission_location),
    ;

    /**
     * Permissions requested together on this API level. Empty when none is needed (always granted).
     * On Android 14+ the photo request includes partial access, and either grant counts.
     */
    val requestPermissions: List<String>
        get() = when {
            this == PHOTOS && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
                listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            else -> listOfNotNull(manifestPermission)
        }

    /** The primary manifest permission on this API level, or null when none is needed (always granted). */
    val manifestPermission: String?
        get() = when (this) {
            CAMERA -> Manifest.permission.CAMERA
            PHOTOS -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_IMAGES
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }
            NOTIFICATIONS -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.POST_NOTIFICATIONS else null
            LOCATION -> Manifest.permission.ACCESS_COARSE_LOCATION
        }
}

/**
 * Remembers which permissions were requested, which Android does not report, so a first-time
 * "not requested" can be told apart from "permanently denied". Cleared with app data.
 */
object PermissionHistory {
    private const val FILE = "permission_history"

    fun wasRequested(context: Context, permission: String) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(permission, false)

    fun markRequested(context: Context, permission: String) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit { putBoolean(permission, true) }
    }
}

fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

fun resolveStatus(context: Context, permission: PlaygroundPermission): PermissionStatus {
    val manifest = permission.manifestPermission ?: return PermissionStatus.GRANTED
    val granted = permission.requestPermissions.any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
    val rationale = context.findActivity()?.shouldShowRequestPermissionRationale(manifest) ?: false
    return PermissionStatusResolver.resolve(granted, rationale, PermissionHistory.wasRequested(context, manifest))
}

@Composable
fun PermissionsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    // Bumped whenever a result arrives or the screen resumes (e.g. back from system Settings).
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }

    PlaygroundScaffold(stringResource(R.string.scenario_permissions), TestTags.PERMISSIONS_SCREEN, onBack) { padding ->
        ScreenColumn(padding) {
            Text(stringResource(R.string.permissions_intro), modifier = Modifier.testTag(textTag(R.string.permissions_intro)))
            PlaygroundPermission.entries.forEach { permission ->
                PermissionCard(permission, refresh) { refresh++ }
            }
            OutlinedButton(
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                    )
                },
                modifier = Modifier.fillMaxWidth().testTag(TestTags.PERMISSION_OPEN_SETTINGS),
            ) { Text(stringResource(R.string.permissions_open_settings)) }
        }
    }
}

@Composable
private fun PermissionCard(permission: PlaygroundPermission, refresh: Int, onResult: () -> Unit) {
    val context = LocalContext.current
    val status = remember(refresh) { resolveStatus(context, permission) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { onResult() }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(permission.label), style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(status.label()),
                    modifier = Modifier.weight(1f).testTag(TestTags.permissionStatus(permission.id)),
                )
                val manifest = permission.manifestPermission
                OutlinedButton(
                    onClick = {
                        if (manifest != null) {
                            PermissionHistory.markRequested(context, manifest)
                            launcher.launch(permission.requestPermissions.toTypedArray())
                        }
                    },
                    // Requesting a permanently denied permission does nothing visible; the button stays
                    // enabled on purpose so tests can verify exactly that.
                    enabled = manifest != null && status != PermissionStatus.GRANTED,
                    modifier = Modifier.testTag(TestTags.permissionRequest(permission.id)),
                ) {
                    Text(
                        stringResource(
                            if (status ==
                                PermissionStatus.NOT_REQUESTED
                            ) {
                                R.string.permissions_request
                            } else {
                                R.string.permissions_request_again
                            },
                        ),
                    )
                }
            }
            if (status == PermissionStatus.PERMANENTLY_DENIED) {
                Text(stringResource(R.string.permissions_permanent_hint), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

fun PermissionStatus.label() = when (this) {
    PermissionStatus.NOT_REQUESTED -> R.string.permission_status_not_requested
    PermissionStatus.GRANTED -> R.string.permission_status_granted
    PermissionStatus.DENIED -> R.string.permission_status_denied
    PermissionStatus.PERMANENTLY_DENIED -> R.string.permission_status_permanently_denied
}
