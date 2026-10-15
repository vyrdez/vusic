package com.feishin.mobile.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.feishin.mobile.ui.theme.VusicColors
import com.feishin.mobile.ui.theme.VusicSpacing

/**
 * The one title row used by every top-level screen (Library, Settings, and
 * anything else at the root of a tab).
 *
 * Two alignment bugs lived here, both fixed:
 *
 * 1. The row had no fixed height, so the title was only centred inside whatever
 *    the tallest child happened to be (Library's 48dp icon buttons vs
 *    Settings' bare text: ~21px apart). The fixed 48dp height centres both
 *    identically.
 * 2. The status-bar inset was counted TWICE: the scaffold's content padding
 *    already starts below the status bar (every inset-less screen — Search,
 *    Home, the Settings sub-screens — relies on exactly that), and this row
 *    added its own status inset on top, but as dead space BELOW the 64dp row
 *    (the row sat at the top of a 64dp+inset box). That alone left ~28dp of
 *    nothing under every title. The row now takes no inset of its own; the
 *    status area comes from the scaffold exactly once.
 */
object TopLevelTitleBar {
    /** Fixed row height. 48dp keeps the title's bottom edge 8dp above content. */
    val RowHeight = 48.dp
}

@Composable
fun TopLevelTitleBar(
    title: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(TopLevelTitleBar.RowHeight)
            .padding(start = VusicSpacing.ScreenHorizontal, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            color = VusicColors.Accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        actions()
    }
}

/** Status-bar spacer for screens that own their top edge (none do by default). */
@Composable
fun StatusBarSpacer(modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Spacer(
        modifier
            .fillMaxWidth()
            .height(WindowInsets.statusBars.asPaddingValues().calculateTopPadding()),
    )
}
