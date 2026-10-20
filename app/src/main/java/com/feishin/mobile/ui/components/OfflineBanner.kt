package com.feishin.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp
import com.feishin.mobile.ui.theme.VusicColors

/**
 * The one offline banner, shared by every screen that needs one.
 *
 * It is a LAYOUT ROW and never an overlay: it always claims its own height, so
 * the content below it is pushed down instead of being covered. [clipToBounds]
 * keeps the text inside the row while the height animates (grow / shrink), so
 * a transition can never leave a clipped line, a 1dp strip or a stray shadow
 * behind a title or a search field.
 *
 * Offline copy lives at the call sites, not here: the app-wide banner and a
 * screen-specific hint say different things and both must stay unchanged.
 */
@Composable
fun OfflineBanner(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clipToBounds()
            .background(VusicColors.SurfaceHigh)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.CloudOff, null,
            tint = VusicColors.SecondaryText,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = VusicColors.SecondaryText,
        )
    }
}
