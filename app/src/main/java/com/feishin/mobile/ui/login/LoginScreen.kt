package com.feishin.mobile.ui.login

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.feishin.mobile.data.settings.SettingsStore
import com.feishin.mobile.ui.theme.Motion
import com.feishin.mobile.ui.theme.VusicColors
import com.feishin.mobile.ui.theme.VusicControlDefaults
import com.feishin.mobile.ui.theme.VusicSpacing

/** Corner radius shared by both login buttons. */
private val ButtonShape = RoundedCornerShape(12.dp)

@Composable
fun LoginScreen(vm: LoginViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsState()
    val settings by vm.settings.flow.collectAsState(initial = null)
    // Survive rotation; the password is intentionally kept out of saved state so
    // process death never persists it to disk (user retypes it).
    var url by rememberSaveable { mutableStateOf("") }
    var user by rememberSaveable { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    val selfSigned = settings?.allowSelfSigned == true

    // A Surface is required, not cosmetic: this screen used to be a bare Column,
    // so LocalContentColor kept Compose's default Color.Black and every Text
    // without an explicit colour (the title, the subtitle and the self-signed
    // switch's label) was drawn black on the black background — invisible. The
    // field labels still showed because OutlinedTextField takes its colours from
    // the scheme rather than from the content colour.
    Surface(
        Modifier.fillMaxSize(),
        color = VusicColors.Background,
        contentColor = Color.White,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                // Scrolls so the fields and both buttons stay reachable on a
                // small screen with the keyboard open.
                .verticalScroll(rememberScrollState())
                .padding(horizontal = VusicSpacing.ScreenHorizontal * 1.5f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "Vusic",
                style = MaterialTheme.typography.displaySmall,
                color = VusicColors.Accent,
            )
            Text(
                "Connect to your Navidrome server",
                style = MaterialTheme.typography.bodyMedium,
                color = VusicColors.SecondaryText,
            )
            Spacer(Modifier.height(VusicSpacing.SectionBottom * 3))
            OutlinedTextField(
                value = url, onValueChange = { url = it },
                label = { Text("Server URL (https://host:4533/music)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(VusicSpacing.CardGap / 2))
            OutlinedTextField(
                value = user, onValueChange = { user = it },
                label = { Text("Username") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(VusicSpacing.CardGap / 2))
            OutlinedTextField(
                value = pass, onValueChange = { pass = it },
                label = { Text("Password") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
            )
            Spacer(Modifier.height(VusicSpacing.SectionBottom * 2))

            // Full-width labelled row: title, one-line explanation, switch on the
            // right, all vertically centred, 48dp minimum, whole row tappable and
            // left-aligned with the fields above.
            val toggle = { on: Boolean -> vm.setAllowSelfSigned(on) }
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clip(RoundedCornerShape(12.dp))
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
                Switch(
                    checked = selfSigned,
                    onCheckedChange = toggle,
                    colors = VusicControlDefaults.switchColors(),
                )
            }

            // Inline warning, animated in and out.
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

            ui.message?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Spacer(Modifier.height(VusicSpacing.CardGap))
            if (ui.busy) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                val enabled = url.isNotBlank() && user.isNotBlank()
                // Same height, corner radius and side padding for both, so the
                // pair reads as one control.
                Button(
                    onClick = { vm.testAndConnect(url, user, pass) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    enabled = enabled,
                    shape = ButtonShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VusicColors.Accent,
                        contentColor = VusicColors.OnAccent,
                    ),
                ) { Text("Test & Connect") }
                Spacer(Modifier.height(VusicSpacing.CardGap / 2))
                OutlinedButton(
                    onClick = { vm.testOnly(url, user, pass) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    enabled = enabled,
                    shape = ButtonShape,
                ) { Text("Test connection only") }
            }
            Spacer(Modifier.height(VusicSpacing.SectionBottom * 2))
        }
    }
}