package com.feishin.mobile.ui.login

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.feishin.mobile.ui.components.UiMessenger
import com.feishin.mobile.ui.theme.Motion
import com.feishin.mobile.ui.theme.VusicColors
import com.feishin.mobile.ui.theme.VusicControlDefaults
import com.feishin.mobile.ui.theme.VusicSpacing

/** Corner radius shared by login fields and buttons. */
private val LoginShape = RoundedCornerShape(12.dp)

@Composable
fun LoginScreen(
    vm: LoginViewModel = hiltViewModel(),
    messenger: UiMessenger,
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val settings by vm.settings.flow.collectAsStateWithLifecycle(initialValue = null)
    // Survive rotation; the password is intentionally kept out of saved state so
    // process death never persists it to disk (user retypes it).
    var url by rememberSaveable { mutableStateOf("") }
    var user by rememberSaveable { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    val selfSigned = settings?.allowSelfSigned == true

    // Clear the inline error whenever the user edits a field.
    val onUrlChange = { text: String -> url = text; vm.clearError() }
    val onUserChange = { text: String -> user = text; vm.clearError() }
    val onPassChange = { text: String -> pass = text; vm.clearError() }

    Surface(
        Modifier.fillMaxSize(),
        color = VusicColors.Background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = VusicSpacing.ScreenHorizontal * 1.5f),
            verticalArrangement = Arrangement.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = VusicColors.Accent,
                    modifier = Modifier.size(36.dp),
                )
                Spacer(Modifier.width(VusicSpacing.CardGap))
                Text(
                    "Vusic",
                    style = MaterialTheme.typography.displaySmall,
                    color = VusicColors.Accent,
                )
            }
            Text(
                "Connect to your Navidrome server",
                style = MaterialTheme.typography.bodyMedium,
                color = VusicColors.SecondaryText,
            )
            Spacer(Modifier.height(VusicSpacing.SectionBottom * 3))

            // Filled, rounded fields matching the app's search field.
            OutlinedTextField(
                value = url,
                onValueChange = onUrlChange,
                label = { Text("Server URL") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                singleLine = true,
            )
            Spacer(Modifier.height(VusicSpacing.CardGap / 2))
            OutlinedTextField(
                value = user,
                onValueChange = onUserChange,
                label = { Text("Username") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                singleLine = true,
            )
            Spacer(Modifier.height(VusicSpacing.CardGap / 2))
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                OutlinedTextField(
                    value = pass,
                    onValueChange = onPassChange,
                    label = { Text("Password") },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    singleLine = true,
                )
            }
            Spacer(Modifier.height(VusicSpacing.SectionBottom * 2))

            val toggle = { on: Boolean -> vm.setAllowSelfSigned(on) }
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clip(LoginShape)
                    .clickable(
                        role = Role.Switch,
                        onClick = { toggle(!selfSigned) },
                    )
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(end = VusicSpacing.ControlGap)) {
                    Text(
                        "Allow self-signed certificate",
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 2,
                    )
                    Text(
                        "Only for servers you trust, such as a home server",
                        style = MaterialTheme.typography.bodySmall,
                        color = VusicColors.SecondaryText,
                        maxLines = 2,
                    )
                }
                androidx.compose.material3.Switch(
                    checked = selfSigned,
                    onCheckedChange = toggle,
                    colors = VusicControlDefaults.switchColors(),
                )
            }

            AnimatedVisibility(
                visible = selfSigned,
                enter = fadeIn(Motion.fade(Motion.Fast)) +
                    expandVertically(animationSpec = Motion.layoutSize()),
                exit = fadeOut(Motion.fade(Motion.Fast)) +
                    shrinkVertically(animationSpec = Motion.layoutSize()),
            ) {
                Text(
                    "Connections to this server won't be fully verified",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                )
            }

            // Inline error card: theme error colors, icon, dismissible,
            // clears when the user edits a field.
            ui.message?.let { message ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = VusicSpacing.ScreenHorizontal * 1.5f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f)),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            maxLines = 2,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        TextButton(
                            onClick = { vm.clearError() },
                        ) {
                            Text("Dismiss")
                        }
                    }
                }
            }
            Spacer(Modifier.height(VusicSpacing.CardGap))
            if (ui.busy) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                val enabled = url.isNotBlank() && user.isNotBlank()
                Button(
                    onClick = { vm.testAndConnect(url, user, pass) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    enabled = enabled,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VusicColors.Accent,
                        contentColor = VusicColors.OnAccent,
                    ),
                ) { Text("Test & Connect") }
                Spacer(Modifier.height(VusicSpacing.CardGap / 2))
                Button(
                    onClick = { vm.testOnly(url, user, pass) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    enabled = enabled,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                ) { Text("Test connection only") }
            }
            Spacer(Modifier.height(VusicSpacing.SectionBottom * 2))
        }
    }

    // Snackbar (shared message host per section A) for the same error.
    ui.message?.let { messenger.show(it) }
}
