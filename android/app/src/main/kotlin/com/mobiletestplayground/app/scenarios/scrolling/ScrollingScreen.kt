package com.mobiletestplayground.app.scenarios.scrolling

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mobiletestplayground.app.R
import com.mobiletestplayground.core.ui.TestTags
import com.mobiletestplayground.core.ui.components.PlaygroundScaffold
import kotlinx.coroutines.launch

private const val SECTION_COUNT = 40
private const val CARD_COUNT = 20

/**
 * A long vertical list (header, horizontal carousel, 40 sections, end marker) for practising
 * scroll-until-visible, horizontal scrolling and programmatic scroll buttons.
 */
@Composable
fun ScrollingScreen(onBack: () -> Unit) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val firstVisible by remember { derivedStateOf { listState.firstVisibleItemIndex } }
    // Index layout: 0 = carousel, 1..SECTION_COUNT = sections, SECTION_COUNT + 1 = end marker.
    val lastIndex = SECTION_COUNT + 1

    PlaygroundScaffold(stringResource(R.string.scenario_scrolling), TestTags.SCROLLING_SCREEN, onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    scope.launch { listState.animateScrollToItem(0) }
                }, modifier = Modifier.testTag(TestTags.SCROLL_TO_TOP)) {
                    Text(stringResource(R.string.scrolling_top))
                }
                OutlinedButton(
                    onClick = { scope.launch { listState.animateScrollToItem(lastIndex) } },
                    modifier = Modifier.testTag(TestTags.SCROLL_TO_BOTTOM),
                ) { Text(stringResource(R.string.scrolling_bottom)) }
            }
            Text(
                stringResource(R.string.scrolling_position, firstVisible),
                modifier = Modifier.padding(horizontal = 16.dp).testTag(TestTags.SCROLL_POSITION),
            )
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().testTag(TestTags.SCROLL_CONTAINER),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().testTag(TestTags.SCROLL_HORIZONTAL),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(CARD_COUNT) { index ->
                            Card(Modifier.size(120.dp, 80.dp).testTag(TestTags.scrollCard(index + 1))) {
                                Text(stringResource(R.string.scrolling_card, index + 1), modifier = Modifier.padding(12.dp))
                            }
                        }
                    }
                }
                items(SECTION_COUNT) { index ->
                    Card(Modifier.fillMaxWidth().height(96.dp).testTag(TestTags.scrollSection(index + 1))) {
                        Text(
                            stringResource(R.string.scrolling_section, index + 1),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
                item {
                    Text(
                        stringResource(R.string.scrolling_end),
                        modifier = Modifier.padding(vertical = 24.dp).testTag(TestTags.SCROLL_BOTTOM_MARKER),
                    )
                }
            }
        }
    }
}
