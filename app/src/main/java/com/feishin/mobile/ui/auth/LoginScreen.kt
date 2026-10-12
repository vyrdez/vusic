package com.feishin.mobile.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun LoginScreen(vm: AuthViewModel = hiltViewModel(), onLoggedIn: () -> Unit) {
    val ui by vm.ui.collectAsState()
    var url by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
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
        ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(8.dp))
        if (ui.loading) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = { vm.login(url, user, pass, onLoggedIn) },
                modifier = Modifier.fillMaxWidth(),
                enabled = url.isNotBlank() && user.isNotBlank(),
            ) { Text("Login") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { vm.test(url, user, pass) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !ui.testing && url.isNotBlank() && user.isNotBlank(),
            ) { Text(if (ui.testing) "Testing…" else "Test connection") }
        }
    }
}
