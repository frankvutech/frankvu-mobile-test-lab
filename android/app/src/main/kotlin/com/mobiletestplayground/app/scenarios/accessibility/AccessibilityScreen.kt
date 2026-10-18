package com.mobiletestplayground.app.scenarios.accessibility

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.mobiletestplayground.app.R
import com.mobiletestplayground.core.ui.TestTags
import com.mobiletestplayground.core.ui.components.PlaygroundScaffold
import com.mobiletestplayground.core.ui.components.ScreenColumn
import com.mobiletestplayground.core.ui.components.SectionTitle
import com.mobiletestplayground.core.ui.components.ValueRow

/**
 * Examples of correct semantics for TalkBack and for automation that queries accessibility data:
 * headings, icon-only buttons with labels and state, merged cards, custom actions, live regions,
 * minimum touch targets, and informative versus decorative images.
 */
@Composable
fun AccessibilityScreen(onBack: () -> Unit) {
    var favorite by rememberSaveable { mutableStateOf(false) }
    var announcements by rememberSaveable { mutableIntStateOf(0) }
    var customAction by rememberSaveable { mutableStateOf<String?>(null) }

    PlaygroundScaffold(stringResource(R.string.scenario_accessibility), TestTags.ACCESSIBILITY_SCREEN, onBack) { padding ->
        ScreenColumn(padding) {
            Text(
                stringResource(R.string.a11y_heading),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.testTag(TestTags.A11Y_HEADING).semantics { heading() },
            )
            Text(stringResource(R.string.a11y_intro))

            SectionTitle(stringResource(R.string.a11y_icon_button))
            val favoriteLabel = stringResource(R.string.a11y_favorite)
            val favoriteState = stringResource(if (favorite) R.string.a11y_favorite_on else R.string.a11y_favorite_off)
            IconToggleButton(
                checked = favorite,
                onCheckedChange = { favorite = it },
                modifier = Modifier
                    .testTag(TestTags.A11Y_FAVORITE)
                    .semantics {
                        contentDescription = favoriteLabel
                        stateDescription = favoriteState
                    },
            ) { Icon(if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, contentDescription = null) }

            SectionTitle(stringResource(R.string.a11y_merged))
            // mergeDescendants: TalkBack reads the card as one item instead of three fragments.
            Card(Modifier.fillMaxWidth().testTag(TestTags.A11Y_MERGED_CARD).semantics(mergeDescendants = true) {}) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Star, contentDescription = null)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text("Product 001", style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.a11y_rating))
                    }
                }
            }

            SectionTitle(stringResource(R.string.a11y_custom_actions))
            val archive = stringResource(R.string.dialogs_action_archive)
            val share = stringResource(R.string.dialogs_action_share)
            Card(
                Modifier
                    .fillMaxWidth()
                    .testTag(TestTags.A11Y_CUSTOM_ACTION_ITEM)
                    .semantics(mergeDescendants = true) {
                        customActions = listOf(
                            CustomAccessibilityAction(archive) {
                                customAction = "archive"
                                true
                            },
                            CustomAccessibilityAction(share) {
                                customAction = "share"
                                true
                            },
                        )
                    },
            ) { Text(stringResource(R.string.a11y_custom_actions_body), modifier = Modifier.padding(16.dp)) }
            ValueRow(
                stringResource(R.string.a11y_last_action),
                customAction ?: stringResource(R.string.value_none),
                TestTags.A11Y_CUSTOM_ACTION_RESULT,
            )

            SectionTitle(stringResource(R.string.a11y_live_region))
            Text(
                stringResource(R.string.a11y_announcements, announcements),
                modifier = Modifier.testTag(TestTags.A11Y_LIVE_REGION).semantics { liveRegion = LiveRegionMode.Polite },
            )
            Button(onClick = { announcements++ }, modifier = Modifier.testTag(TestTags.A11Y_LIVE_REGION_TRIGGER)) {
                Text(stringResource(R.string.a11y_announce))
            }

            SectionTitle(stringResource(R.string.a11y_touch_target))
            // 48dp minimum touch target, even though the visible label is small.
            Button(onClick = {}, modifier = Modifier.size(width = 160.dp, height = 56.dp).testTag(TestTags.A11Y_LARGE_TARGET)) {
                Text(stringResource(R.string.a11y_large_target), style = MaterialTheme.typography.labelSmall)
            }

            SectionTitle(stringResource(R.string.a11y_images))
            Row {
                Image(
                    imageVector = Icons.Filled.Star,
                    contentDescription = stringResource(R.string.a11y_image_description),
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.size(48.dp).testTag(TestTags.A11Y_INFORMATIVE_IMAGE),
                )
                // Decorative: no content description, so TalkBack skips it.
                Image(
                    imageVector = Icons.Filled.Wallpaper,
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.outline),
                    modifier = Modifier.size(48.dp).testTag(TestTags.A11Y_DECORATIVE_IMAGE),
                )
            }
        }
    }
}
