package com.feishin.mobile.ui.components

import androidx.compose.foundation.layout.Box
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
 * It exists because the titles had drifted apart by ~21px on the same device.
 * The row had no fixed height, so the title was only centred inside whatever
 * the tallest child happened to be: Library carries three 48dp icon buttons, so
 * its text floated (48-29)/2 = 9.5dp lower than Settings', which has nothing but
 * the text. Fixing the height, the status-bar inset handling, the start padding
 * and the typography in one place makes the baselines match by construction.
 */
object TopLevelTitleBar {
    /** Fixed row height, excluding the status-bar inset. */
    val RowHeight = 64.dp
}

@Composable
fun TopLevelTitleBar(
    title: String,
    modifier: Modifier = Modifier,
    applyStatusInset: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val topInset = if (applyStatusInset) {
        WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    } else {
        0.dp
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(TopLevelTitleBar.RowHeight + topInset),
    ) {
        Row(
            Modifier
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
}