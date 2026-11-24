package com.feishin.mobile.ui.login

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.feishin.mobile.domain.model.BackendType
import com.feishin.mobile.ui.components.UiMessenger
import com.feishin.mobile.ui.theme.Motion
import com.feishin.mobile.ui.theme.VusicColors
import com.feishin.mobile.ui.theme.VusicControlDefaults
import com.feishin.mobile.ui.theme.VusicSpacing

/** Corner radius shared by login fields and buttons (matches the search field). */
private val FieldShape = RoundedCornerShape(24.dp)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LoginScreen(
    vm: LoginViewModel = hiltViewModel(),
    messenger: UiMessenger,
    addMode: Boolean = false,
    onAdded: () -> Unit = {},
    onCancel: () -> Unit = {},
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val settings by vm.settings.flow.collectAsStateWithLifecycle(initialValue = null)
    // Survive rotation; the password is intentionally kept out of saved state so
    // process death never persists it to disk (user retypes it).
    var backendType by rememberSaveable { mutableStateOf(BackendType.NAVIDROME) }
    var serverName by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    var user by rememberSaveable { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var allowSelfSigned by rememberSaveable { mutableStateOf(false) }
    var allowCleartext by rememberSaveable { mutableStateOf(true) }
    var seededFromSettings by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(settings) {
        val current = settings
        if (current != null && !seededFromSettings) {
            allowSelfSigned = current.allowSelfSigned
            allowCleartext = current.allowCleartext
            seededFromSettings = true
        }
    }

    LaunchedEffect(addMode) {
        if (addMode) vm.resetUi()
    }

    val busy = ui.busy

    if (addMode) {
        BackHandler(enabled = !busy) { onCancel() }
    }

    // Clear the inline error whenever the user edits a field.
    val onNameChange = { text: String -> serverName = text; vm.clearError() }
    val onUrlChange = { text: String -> url = text; vm.clearError() }
    val onUserChange = { text: String -> user = text; vm.clearError() }
    val onPassChange = { text: String -> pass = text; vm.clearError() }

    val canSubmit = url.isNotBlank() && user.isNotBlank() && !busy
    // Typed as Unit-returning so it can be passed straight to onClick/onDone.
    val submit: () -> Unit = {
        if (canSubmit) {
            vm.testAndConnect(
                serverUrl = url.trim(),
                username = user.trim(),
                password = pass,
                backendType = backendType,
                serverName = serverName.trim(),
                allowSelfSigned = allowSelfSigned,
                allowCleartext = allowCleartext,
                addMode = addMode,
                onSuccess = onAdded,
            )
        }
    }
    val submitTestOnly: () -> Unit = {
        if (canSubmit) {
            vm.testOnly(
                serverUrl = url.trim(),
                username = user.trim(),
                password = pass,
                allowSelfSigned = allowSelfSigned,
                allowCleartext = allowCleartext,
            )
        }
    }

    // The same failure is also raised as a snackbar through the section-A host,
    // so it stays visible above the mini player even once this screen scrolls.
    LaunchedEffect(ui.failureNonce) {
        val text = ui.message
        if (text != null && ui.isError) messenger.show(text)
    }

    Surface(
        Modifier.fillMaxSize(),
        color = VusicColors.Background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            if (addMode) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, end = 12.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onCancel, enabled = !busy, modifier = Modifier.size(48.dp)) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Cancel",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Text(
                        "Add server",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onCancel, enabled = !busy) {
                        Text("Cancel")
                    }
                }
            }

            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(horizontal = VusicSpacing.ScreenHorizontal * 1.5f),
                verticalArrangement = if (addMode) Arrangement.Top else Arrangement.Center,
            ) {
                if (addMode) {
                    Spacer(Modifier.height(VusicSpacing.SectionBottom))
                } else {
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
                }
                Text(
                    when (backendType) {
                        BackendType.OPENSUBSONIC -> "Connect to your OpenSubsonic server"
                        else -> "Connect to your Navidrome server"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = VusicColors.SecondaryText,
                )
                Spacer(Modifier.height(VusicSpacing.SectionBottom * 2))

                Text(
                    "Backend",
                    style = MaterialTheme.typography.labelMedium,
                    color = VusicColors.SecondaryText,
                )
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(
                        BackendType.NAVIDROME to "Navidrome",
                        BackendType.OPENSUBSONIC to "OpenSubsonic",
                    ).forEach { (type, label) ->
                        val selected = backendType == type
                        FilterChip(
                            selected = selected,
                            onClick = {
                                if (!busy) {
                                    backendType = type
                                    vm.clearError()
                                }
                            },
                            enabled = !busy,
                            label = {
                                Text(
                                    label,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            leadingIcon = if (selected) {
                                {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = VusicColors.Accent.copy(alpha = 0.18f),
                                selectedLabelColor = VusicColors.Accent,
                                selectedLeadingIconColor = VusicColors.Accent,
                            ),
                        )
                    }
                    FilterChip(
                        selected = false,
                        onClick = {},
                        enabled = false,
                        label = { Text("Jellyfin · Soon", maxLines = 1) },
                    )
                    FilterChip(
                        selected = false,
                        onClick = {},
                        enabled = false,
                        label = { Text("Google Drive · Soon", maxLines = 1) },
                    )
                }
                Spacer(Modifier.height(VusicSpacing.SectionBottom * 2))

                val fieldColors = TextFieldDefaults.colors(
                    focusedContainerColor = VusicColors.SurfaceHigh,
                    unfocusedContainerColor = VusicColors.SurfaceHigh,
                    disabledContainerColor = VusicColors.SurfaceHigh.copy(alpha = 0.4f),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                )

                // Filled, rounded fields matching the app's search field.
                TextField(
                    value = serverName,
                    onValueChange = onNameChange,
                    label = { Text("Server name (optional)") },
                    placeholder = {
                        Text("Defaults to host name", color = VusicColors.SecondaryText)
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    singleLine = true,
                    enabled = !busy,
                    shape = FieldShape,
                    colors = fieldColors,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next,
                    ),
                )
                Spacer(Modifier.height(VusicSpacing.CardGap / 2))
                TextField(
                    value = url,
                    onValueChange = onUrlChange,
                    label = { Text("Server URL") },
                    placeholder = {
                        Text("https://host:4533/music", color = VusicColors.SecondaryText)
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    singleLine = true,
                    enabled = !busy,
                    shape = FieldShape,
                    colors = fieldColors,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next,
                    ),
                )
                Spacer(Modifier.height(VusicSpacing.CardGap / 2))
                TextField(
                    value = user,
                    onValueChange = onUserChange,
                    label = { Text("Username") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    singleLine = true,
                    enabled = !busy,
                    shape = FieldShape,
                    colors = fieldColors,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next,
                    ),
                )
                Spacer(Modifier.height(VusicSpacing.CardGap / 2))
                TextField(
                    value = pass,
                    onValueChange = onPassChange,
                    label = { Text("Password") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    singleLine = true,
                    enabled = !busy,
                    shape = FieldShape,
                    colors = fieldColors,
                    visualTransformation = if (showPassword) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = { showPassword = !showPassword },
                            enabled = !busy,
                        ) {
                            Icon(
                                imageVector = if (showPassword) {
                                    Icons.Filled.VisibilityOff
                                } else {
                                    Icons.Filled.Visibility
                                },
                                contentDescription = if (showPassword) {
                                    "Hide password"
                                } else {
                                    "Show password"
                                },
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                )
                Spacer(Modifier.height(VusicSpacing.SectionBottom * 2))

                LoginToggleRow(
                    title = "Allow self-signed certificate",
                    subtitle = "Only for servers you trust, such as a home server",
                    checked = allowSelfSigned,
                    enabled = !busy,
                    onToggle = { allowSelfSigned = it; vm.clearError() },
                )

                AnimatedVisibility(
                    visible = allowSelfSigned,
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

                LoginToggleRow(
                    title = "Allow cleartext HTTP",
                    subtitle = "Allow unencrypted http:// connections for this server",
                    checked = allowCleartext,
                    enabled = !busy,
                    onToggle = { allowCleartext = it; vm.clearError() },
                )

                AnimatedVisibility(
                    visible = allowCleartext && url.trim().startsWith("http://", ignoreCase = true),
                    enter = fadeIn(Motion.fade(Motion.Fast)) +
                        expandVertically(animationSpec = Motion.layoutSize()),
                    exit = fadeOut(Motion.fade(Motion.Fast)) +
                        shrinkVertically(animationSpec = Motion.layoutSize()),
                ) {
                    Text(
                        "Traffic can be read on public networks",
                        style = MaterialTheme.typography.bodySmall,
                        color = VusicColors.Warning,
                        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                    )
                }

                // Inline error / confirmation card above the buttons.
                AnimatedVisibility(
                    visible = ui.message != null,
                    enter = fadeIn(Motion.fade(Motion.Fast)) +
                        expandVertically(animationSpec = Motion.layoutSize()),
                    exit = fadeOut(Motion.fade(Motion.Fast)) +
                        shrinkVertically(animationSpec = Motion.layoutSize()),
                ) {
                    ui.message?.let { message ->
                        LoginMessageCard(
                            message = message,
                            isError = ui.isError,
                            details = ui.details,
                            onDismiss = { vm.clearError() },
                        )
                    }
                }

                Spacer(Modifier.height(VusicSpacing.CardGap))

                // Reserved row so the buttons never jump when the spinner appears.
                Box(
                    Modifier.fillMaxWidth().heightIn(min = 28.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (busy) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = VusicColors.Accent,
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "Contacting the server…",
                                style = MaterialTheme.typography.bodySmall,
                                color = VusicColors.SecondaryText,
                            )
                        }
                    }
                }

                Button(
                    onClick = submit,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    enabled = canSubmit,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VusicColors.Accent,
                        contentColor = VusicColors.OnAccent,
                    ),
                ) { Text("Test & Connect") }
                Spacer(Modifier.height(VusicSpacing.CardGap / 2))
                Button(
                    onClick = submitTestOnly,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    enabled = canSubmit,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                ) { Text("Test connection only") }
                if (!addMode) {
                    Spacer(Modifier.height(VusicSpacing.CardGap / 2))
                    OutlinedButton(
                        onClick = { vm.useDeviceFilesOnly() },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        enabled = !busy,
                        shape = CircleShape,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    ) {
                        Text(
                            "Use Device files only",
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                Spacer(Modifier.height(VusicSpacing.SectionBottom * 2))
            }
        }
    }
}

@Composable
private fun LoginToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                enabled = enabled,
                role = Role.Switch,
                onClick = { onToggle(!checked) },
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = VusicSpacing.ControlGap)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = VusicColors.SecondaryText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onToggle,
            enabled = enabled,
            colors = VusicControlDefaults.switchColors(),
        )
    }
}

/**
 * One card for both outcomes: theme error colours for failures, the neutral
 * surface for a successful connection test. Technical details stay collapsed
 * behind "Details" so the first line never wraps past two lines.
 */
@Composable
private fun LoginMessageCard(
    message: String,
    isError: Boolean,
    details: String?,
    onDismiss: () -> Unit,
) {
    var expanded by remember(details) { mutableStateOf(false) }
    val container = if (isError) {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f)
    } else {
        VusicColors.SurfaceHigh
    }
    val content = if (isError) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(container)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            if (isError) {
                Icon(
                    Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = content,
                    modifier = Modifier.padding(top = 2.dp).size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = content,
                maxLines = 2,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onDismiss) {
                Text("Dismiss", color = content)
            }
        }
        if (details != null) {
            TextButton(
                onClick = { expanded = !expanded },
                contentPadding = PaddingValues(0.dp),
            ) {
                Text(
                    if (expanded) "Hide details" else "Details",
                    style = MaterialTheme.typography.labelMedium,
                    color = content,
                )
            }
            if (expanded) {
                Text(
                    text = details,
                    style = MaterialTheme.typography.bodySmall,
                    color = content,
                )
            }
        }
    }
}
