package com.mobiletestplayground.app.scenarios.deeplinks

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import com.mobiletestplayground.app.R
import com.mobiletestplayground.app.navigation.DeepLinkRouter
import com.mobiletestplayground.core.ui.TestTags
import com.mobiletestplayground.core.ui.components.ErrorText
import com.mobiletestplayground.core.ui.components.PlaygroundScaffold
import com.mobiletestplayground.core.ui.components.ScreenColumn
import com.mobiletestplayground.core.ui.components.SectionTitle
import com.mobiletestplayground.core.ui.components.textTag

/** Example links, also documented in docs/automation.md with the matching adb and simctl commands. */
val deepLinkExamples = listOf(
    "home" to "mtp://playground/home",
    "forms" to "mtp://playground/scenario/forms",
    "product" to "mtp://playground/product/product_003",
    "cart" to "mtp://playground/cart",
    "admin" to "mtp://playground/admin",
    "detail" to "mtp://playground/detail/3",
)

/**
 * Opens links in-app through the same router the activity uses for external intents, so the
 * access policy applies identically. External launch is covered by UIAutomator tests and adb.
 */
@Composable
fun DeepLinksScreen(onBack: () -> Unit, onOpenRoute: (String) -> Unit) {
    var input by rememberSaveable { mutableStateOf("mtp://playground/") }
    var invalid by rememberSaveable { mutableStateOf(false) }
    PlaygroundScaffold(stringResource(R.string.scenario_deep_links), TestTags.DEEP_LINKS_SCREEN, onBack) { padding ->
        ScreenColumn(padding) {
            Text(stringResource(R.string.deep_links_intro), modifier = Modifier.testTag(textTag(R.string.deep_links_intro)))
            SectionTitle(R.string.deep_links_examples)
            deepLinkExamples.forEach { (name, link) ->
                OutlinedButton(
                    onClick = { DeepLinkRouter.routeFor(link)?.let(onOpenRoute) },
                    modifier = Modifier.fillMaxWidth().testTag(TestTags.deepLinkExample(name)),
                ) { Text(link, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
            }
            SectionTitle(R.string.deep_links_custom)
            OutlinedTextField(
                value = input,
                onValueChange = {
                    input = it
                    invalid = false
                },
                singleLine = true,
                label = { Text(stringResource(R.string.deep_links_url)) },
                modifier = Modifier.fillMaxWidth().testTag(TestTags.DEEP_LINK_INPUT),
            )
            if (invalid) ErrorText(stringResource(R.string.deep_links_invalid), TestTags.DEEP_LINK_RESULT)
            Button(
                onClick = {
                    val route = DeepLinkRouter.routeFor(input.trim())
                    invalid = route == null
                    route?.let(onOpenRoute)
                },
                modifier = Modifier.fillMaxWidth().testTag(TestTags.DEEP_LINK_OPEN),
            ) { Text(stringResource(R.string.deep_links_open)) }
        }
    }
}
