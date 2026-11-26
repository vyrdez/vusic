package com.feishin.mobile.ui.login

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.feishin.mobile.domain.model.BackendType
import com.feishin.mobile.domain.model.ProfileCredentials
import com.feishin.mobile.domain.model.ServerProfile
import com.feishin.mobile.ui.components.UiMessenger
import com.feishin.mobile.ui.settings.LoginToggleRow
import com.feishin.mobile.ui.settings.SettingsCard
import com.feishin.mobile.ui.theme.Motion.pressScale
import com.feishin.mobile.ui.theme.VusicColors
import com.feishin.mobile.ui.theme.VusicSpacing

private val FieldShape = RoundedCornerShape(14.dp)
private val BrandMarkShape = RoundedCornerShape(12.dp)

@Composable
fun LoginScreen(
    vm: LoginViewModel = hiltViewModel(),
    messenger: UiMessenger,
    addMode: Boolean = false,
    onAdded: () -> Unit = {},
    onCancel: () -> Unit = {},
    onUseDeviceFilesOnly: () -> Unit = {},
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val busy = ui.busy
    val reduceMotion = rememberLoginReduceMotion()
    var selectedBackend by rememberSaveable { mutableStateOf(BackendType.NAVIDROME) }

    if (addMode) {
        BackHandler(enabled = !busy) { onCancel() }
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
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Text(
                        "Add server",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = onCancel,
                        enabled = !busy,
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                    ) {
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
                verticalArrangement = Arrangement.Top,
            ) {
                if (addMode) {
                    Spacer(Modifier.height(VusicSpacing.SectionBottom))
                } else {
                    Spacer(Modifier.height(26.dp))
                    LoginBrandHeader(
                        backend = selectedBackend,
                        reduceMotion = reduceMotion,
                    )
                    Spacer(Modifier.height(24.dp))
                }

                ServerFormContent(
                    vm = vm,
                    messenger = messenger,
                    existingProfile = null,
                    addMode = addMode,
                    onBackendChanged = { selectedBackend = it },
                    onSuccess = onAdded,
                ) {
                    if (!addMode) {
                        Spacer(Modifier.height(14.dp))
                        OrDivider()
                        Spacer(Modifier.height(14.dp))
                        val deviceInteraction = remember { MutableInteractionSource() }
                        val devicePressed by deviceInteraction.collectIsPressedAsState()
                        val deviceTint = if (devicePressed) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            VusicColors.SecondaryText
                        }
                        val deviceScaleMod = if (reduceMotion) {
                            Modifier
                        } else {
                            Modifier.pressScale(pressed = devicePressed && !busy, targetScale = 0.985f)
                        }
                        OutlinedButton(
                            onClick = {
                                vm.useDeviceFilesOnly()
                                onUseDeviceFilesOnly()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .then(deviceScaleMod),
                            enabled = !busy,
                            interactionSource = deviceInteraction,
                            shape = CircleShape,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.PhoneAndroid,
                                contentDescription = null,
                                tint = deviceTint,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Use Device files only",
                                color = deviceTint,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(VusicSpacing.SectionBottom * 2))
            }
        }
    }
}

@Composable
private fun LoginBrandHeader(
    backend: BackendType,
    reduceMotion: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(BrandMarkShape)
                .background(VusicColors.SurfaceHigh)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, BrandMarkShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Vusic",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.3).sp,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            AnimatedContent(
                targetState = backend,
                transitionSpec = {
                    if (reduceMotion) {
                        fadeIn(snap()) togetherWith fadeOut(snap())
                    } else {
                        fadeIn(tween(160)) togetherWith fadeOut(tween(120))
                    }
                },
                label = "login-subtitle",
            ) { currentBackend ->
                Text(
                    text = currentBackend.loginSubtitle(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = VusicColors.SecondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun OrDivider() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        Text(
            text = "or",
            style = MaterialTheme.typography.labelSmall,
            color = VusicColors.SecondaryText,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

/**
 * Shared server form body used by first-run login and Settings Add/Edit Server.
 */
@Composable
fun ServerFormContent(
    vm: LoginViewModel,
    messenger: UiMessenger,
    existingProfile: ServerProfile? = null,
    addMode: Boolean = false,
    onBackendChanged: (BackendType) -> Unit = {},
    onSuccess: () -> Unit = {},
    bottomExtraContent: @Composable ColumnScope.() -> Unit = {},
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val existingId = existingProfile?.id
    val editMode = existingId != null
    val reduceMotion = rememberLoginReduceMotion()

    // Text fields survive configuration changes; password stays in plain remember
    // so process death never writes secrets into the saved-instance bundle.
    var backendType by rememberSaveable(existingId) {
        mutableStateOf(existingProfile?.type ?: BackendType.NAVIDROME)
    }
    var serverName by rememberSaveable(existingId) {
        mutableStateOf(existingProfile?.name.orEmpty())
    }
    var url by rememberSaveable(existingId) {
        mutableStateOf(existingProfile?.url.orEmpty())
    }
    var user by rememberSaveable(existingId) {
        mutableStateOf(existingProfile?.let(::existingProfileUsername).orEmpty())
    }
    var pass by remember(existingId) { mutableStateOf("") }
    var showPassword by rememberSaveable(existingId) { mutableStateOf(false) }
    var allowSelfSigned by rememberSaveable(existingId) {
        mutableStateOf(existingProfile?.allowSelfSigned ?: false)
    }
    var allowCleartext by rememberSaveable(existingId) {
        mutableStateOf(existingProfile?.allowCleartext ?: false)
    }

    LaunchedEffect(existingId) {
        vm.resetUi()
    }

    LaunchedEffect(backendType) {
        onBackendChanged(backendType)
    }

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is LoginEvent.Snackbar -> messenger.show(event.message)
            }
        }
    }

    val busy = ui.busy
    val canConnect = LoginViewModel.canConnect(
        url = url,
        username = user,
        password = pass,
        backendType = backendType,
        editMode = editMode,
    )
    val canTest = LoginViewModel.canTest(
        url = url,
        username = user,
        password = pass,
        backendType = backendType,
        editMode = editMode,
    )

    val onConnectClick: () -> Unit = {
        if (!busy) {
            if (canConnect) {
                vm.testAndConnect(
                    serverUrl = url.trim(),
                    username = user.trim(),
                    password = pass,
                    backendType = backendType,
                    serverName = serverName.trim(),
                    allowSelfSigned = allowSelfSigned,
                    allowCleartext = allowCleartext,
                    addMode = addMode || editMode,
                    existingProfileId = existingId,
                    onSuccess = onSuccess,
                )
            } else {
                vm.notifyIncomplete(backendType = backendType, editMode = editMode)
            }
        }
    }

    val onTestOnlyClick: () -> Unit = {
        if (!busy) {
            if (canTest) {
                vm.testOnly(
                    serverUrl = url.trim(),
                    username = user.trim(),
                    password = pass,
                    allowSelfSigned = allowSelfSigned,
                    allowCleartext = allowCleartext,
                    backendType = backendType,
                    existingProfileId = existingId,
                )
            } else {
                vm.notifyIncomplete(backendType = backendType, editMode = editMode)
            }
        }
    }

    Column(Modifier.fillMaxWidth()) {
        FormSectionLabel("BACKEND TYPE")
        Spacer(Modifier.height(10.dp))
        BackendTileGrid(
            selected = backendType,
            enabled = !busy,
            locked = editMode,
            onSelect = { selected ->
                if (backendType != selected) {
                    backendType = selected
                    onBackendChanged(selected)
                    pass = ""
                    vm.clearError()
                }
            },
            onSoonClick = { vm.notifyComingSoon() },
        )

        Spacer(Modifier.height(22.dp))
        FormSectionLabel("CONNECTION & CREDENTIALS")
        Spacer(Modifier.height(10.dp))

        val fieldColors = TextFieldDefaults.colors(
            focusedContainerColor = VusicColors.Surface,
            unfocusedContainerColor = VusicColors.Surface,
            disabledContainerColor = VusicColors.Surface.copy(alpha = 0.5f),
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        )

        SettingsCard {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LabeledFormField(
                    label = "Server name",
                    hint = "Optional",
                    value = serverName,
                    onValueChange = { serverName = it; vm.clearError() },
                    placeholder = "e.g. Home Library",
                    enabled = !busy,
                    colors = fieldColors,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next,
                    ),
                )
                LabeledFormField(
                    label = "Server URL",
                    hint = "HTTPS or HTTP",
                    value = url,
                    onValueChange = { url = it; vm.clearError() },
                    placeholder = if (backendType == BackendType.JELLYFIN) {
                        "https://jellyfin.example.com:8096"
                    } else {
                        "https://host:4533/music"
                    },
                    enabled = !busy,
                    colors = fieldColors,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next,
                    ),
                )
                LabeledFormField(
                    label = "Username",
                    hint = null,
                    value = user,
                    onValueChange = { user = it; vm.clearError() },
                    placeholder = "Enter username",
                    enabled = !busy,
                    colors = fieldColors,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next,
                    ),
                )
                LabeledFormField(
                    label = "Password",
                    hint = if (backendType == BackendType.JELLYFIN) "Optional" else null,
                    value = pass,
                    onValueChange = { pass = it; vm.clearError() },
                    placeholder = if (editMode) "Leave blank to keep current" else "Enter password",
                    enabled = !busy,
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
                            Crossfade(
                                targetState = showPassword,
                                animationSpec = if (reduceMotion) snap() else tween(120),
                                label = "password-visibility",
                            ) { visible ->
                                Icon(
                                    imageVector = if (visible) {
                                        Icons.Filled.VisibilityOff
                                    } else {
                                        Icons.Filled.Visibility
                                    },
                                    contentDescription = if (visible) {
                                        "Hide password"
                                    } else {
                                        "Show password"
                                    },
                                )
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { onConnectClick() }),
                )
            }
        }

        Spacer(Modifier.height(22.dp))
        FormSectionLabel("TRANSPORT & SECURITY")
        Spacer(Modifier.height(10.dp))

        val isHttpUrl = url.trim().startsWith("http://", ignoreCase = true)
        SettingsCard {
            LoginToggleRow(
                title = "Allow self-signed certificate",
                subtitle = "For trusted local or home-lab servers using custom TLS",
                checked = allowSelfSigned,
                enabled = !busy,
                onToggle = { allowSelfSigned = it; vm.clearError() },
                warning = if (allowSelfSigned) {
                    "TLS certificate chain won't be strictly verified"
                } else {
                    null
                },
                warningIsError = true,
                showDivider = true,
            )
            LoginToggleRow(
                title = "Allow cleartext HTTP",
                subtitle = "Permit unencrypted http:// connections on local networks",
                checked = allowCleartext,
                enabled = !busy,
                onToggle = { allowCleartext = it; vm.clearError() },
                warning = if (allowCleartext && isHttpUrl) {
                    "Traffic can be read on public networks"
                } else {
                    null
                },
                warningIsError = false,
                hint = if (!allowCleartext && isHttpUrl) {
                    "This URL uses HTTP — enable cleartext to connect"
                } else {
                    null
                },
                showDivider = false,
            )
        }

        Spacer(Modifier.height(14.dp))
        ConnectionBanner(
            state = ui.banner,
            onDismiss = { vm.dismissBanner() },
        )
        Spacer(Modifier.height(14.dp))

        val primaryInteraction = remember { MutableInteractionSource() }
        val primaryPressed by primaryInteraction.collectIsPressedAsState()
        val primaryScaleMod = if (reduceMotion) {
            Modifier
        } else {
            Modifier.pressScale(pressed = primaryPressed && canConnect && !busy, targetScale = 0.985f)
        }

        Button(
            onClick = onConnectClick,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .then(primaryScaleMod),
            enabled = !busy,
            interactionSource = primaryInteraction,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (canConnect) {
                    VusicColors.Accent
                } else {
                    VusicColors.Accent.copy(alpha = 0.38f)
                },
                contentColor = if (canConnect) {
                    VusicColors.OnAccent
                } else {
                    VusicColors.OnAccent.copy(alpha = 0.65f)
                },
            ),
        ) {
            if (busy && ui.activeAction == LoginAction.CONNECT) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = LocalContentColor.current,
                )
                Spacer(Modifier.width(8.dp))
                Text("Testing…")
            } else {
                Text(if (editMode) "Save & Connect" else "Test & Connect")
            }
        }

        Spacer(Modifier.height(10.dp))

        val secondaryInteraction = remember { MutableInteractionSource() }
        val secondaryPressed by secondaryInteraction.collectIsPressedAsState()
        val secondaryScaleMod = if (reduceMotion) {
            Modifier
        } else {
            Modifier.pressScale(pressed = secondaryPressed && canTest && !busy, targetScale = 0.985f)
        }

        Button(
            onClick = onTestOnlyClick,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .then(secondaryScaleMod),
            enabled = !busy,
            interactionSource = secondaryInteraction,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (canTest) {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f)
                },
                contentColor = if (canTest) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                },
            ),
        ) {
            if (busy && ui.activeAction == LoginAction.TEST_ONLY) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = LocalContentColor.current,
                )
                Spacer(Modifier.width(8.dp))
                Text("Testing…")
            } else {
                Text("Test connection only")
            }
        }

        bottomExtraContent()
    }
}

@Composable
private fun FormSectionLabel(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = VusicColors.SecondaryText,
        modifier = Modifier.padding(start = 4.dp),
    )
}

@Composable
private fun LabeledFormField(
    label: String,
    hint: String?,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    enabled: Boolean,
    colors: androidx.compose.material3.TextFieldColors,
    keyboardOptions: KeyboardOptions,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (!hint.isNullOrBlank()) {
                Text(
                    text = hint,
                    style = MaterialTheme.typography.labelSmall,
                    color = VusicColors.SecondaryText,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        TextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = {
                Text(
                    text = placeholder,
                    color = VusicColors.SecondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
            singleLine = true,
            enabled = enabled,
            shape = FieldShape,
            colors = colors,
            visualTransformation = visualTransformation,
            trailingIcon = trailingIcon,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
        )
    }
}

private fun existingProfileUsername(profile: ServerProfile): String = when (val creds = profile.credentials) {
    is ProfileCredentials.Subsonic -> creds.username
    is ProfileCredentials.Jellyfin -> creds.username
    is ProfileCredentials.GoogleDrive -> creds.accountEmail
}
