package com.feishin.mobile.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.feishin.mobile.data.settings.SettingsStore

@Composable
fun LoginScreen(vm: LoginViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsState()
    val settings by vm.settings.flow.collectAsState(initial = null)
    // Survive rotation; the password is intentionally kept out of saved state so
    // process death never persists it to disk (user retypes it).
    var url by rememberSaveable { mutableStateOf("") }
    var user by rememberSaveable { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Feishin", style = MaterialTheme.typography.displaySmall)
        Text("Connect to your Navidrome server", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(url, { url = it }, label = { Text("Server URL (https://host:4533/music)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(user, { user = it }, label = { Text("Username") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(pass, { pass = it }, label = { Text("Password") }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation())
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Allow self-signed certificate", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Switch(settings?.allowSelfSigned == true, { vm.setAllowSelfSigned(it) })
        }
        Spacer(Modifier.height(4.dp))
        ui.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(8.dp))
        if (ui.busy) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = { vm.testAndConnect(url, user, pass) },
                modifier = Modifier.fillMaxWidth(),
                enabled = url.isNotBlank() && user.isNotBlank(),
            ) { Text("Test & Connect") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { vm.testOnly(url, user, pass) },
                modifier = Modifier.fillMaxWidth(),
                enabled = url.isNotBlank() && user.isNotBlank(),
            ) { Text("Test connection only") }
        }
    }
}
