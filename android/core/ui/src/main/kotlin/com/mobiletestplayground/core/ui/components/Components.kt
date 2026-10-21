package com.mobiletestplayground.core.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mobiletestplayground.core.ui.R
import com.mobiletestplayground.core.ui.TestTags

/** The root ID of the enclosing [PlaygroundScaffold]; screen-level IDs such as the scroll container derive from it. */
val LocalScreenTag = staticCompositionLocalOf<String?> { null }

/** The ID of a static text from the string table: `<string key>_text` (see [TestTags.text]). */
@Composable
fun textTag(@StringRes id: Int): String = TestTags.text(LocalContext.current.resources.getResourceEntryName(id))

/** The scroll container ID of the current screen: `login_screen` → `login_scroll` (see [TestTags.scroll]). */
@Composable
fun screenScrollTag(): String = TestTags.scroll((LocalScreenTag.current ?: "screen").removeSuffix("_screen"))

/**
 * The standard screen frame: a top app bar with a tagged title and optional back button, and a
 * content area tagged with the screen's root ID so tests can assert which screen is showing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaygroundScaffold(
    title: String,
    rootTag: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    snackbarHostState: SnackbarHostState? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        modifier = Modifier
                            .testTag(TestTags.TOP_BAR_TITLE)
                            .semantics { heading() },
                    )
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack, modifier = Modifier.testTag(TestTags.TOP_BAR_BACK)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.ui_navigate_back))
                        }
                    }
                },
                actions = actions,
            )
        },
        snackbarHost = {
            snackbarHostState?.let { state ->
                SnackbarHost(state) { data ->
                    Snackbar(
                        modifier = Modifier.testTag(TestTags.SNACKBAR),
                        action = data.visuals.actionLabel?.let { label ->
                            {
                                TextButton(onClick = data::performAction, modifier = Modifier.testTag(TestTags.SNACKBAR_ACTION)) {
                                    Text(label)
                                }
                            }
                        },
                    ) {
                        Text(data.visuals.message, modifier = Modifier.testTag(TestTags.SNACKBAR_MESSAGE))
                    }
                }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .testTag(rootTag),
        ) {
            CompositionLocalProvider(LocalScreenTag provides rootTag) { content(padding) }
        }
    }
}

/** A scrolling column with the standard screen padding, tagged as the screen's scroll container. */
@Composable
fun ScreenColumn(
    padding: PaddingValues,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .testTag(screenScrollTag())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

/** A section heading from the string table, tagged `<string key>_text`. */
@Composable
fun SectionTitle(@StringRes textRes: Int, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(textRes),
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier
            .testTag(textTag(textRes))
            .semantics { heading() },
    )
}

/**
 * A label/value pair. The value carries the tag so tests assert on exactly the state text, e.g.
 * `onNodeWithTag("controls_switch_state").assertTextEquals("ON")`; the label is `<value tag>_label`.
 */
@Composable
fun ValueRow(
    label: String,
    value: String,
    valueTag: String,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).testTag(TestTags.label(valueTag)))
        Spacer(Modifier.width(8.dp))
        Text(value, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag(valueTag))
    }
}

/** A row-sized switch: the whole row is one toggleable node, which is what TalkBack expects. */
@Composable
fun LabeledSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
fun LabeledCheckbox(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

/** A validation message announced politely by screen readers and marked as an error. */
@Composable
fun ErrorText(text: String, tag: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
        modifier = modifier
            .testTag(tag)
            .semantics {
                error(text)
                liveRegion = LiveRegionMode.Polite
            },
    )
}

@Composable
fun LoadingIndicator(tag: String, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.ui_loading)
    CircularProgressIndicator(
        modifier = modifier
            .testTag(tag)
            .semantics { contentDescription = description },
    )
}

@Composable
fun CenteredLoading(tag: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        LoadingIndicator(tag)
    }
}

/** A remembered snackbar host for screens that show transient messages. */
@Composable
fun rememberSnackbarHost(): SnackbarHostState = remember { SnackbarHostState() }
