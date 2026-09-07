package com.aarush.cpm.ui.login

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aarush.cpm.R
import com.aarush.cpm.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LoginUiState(
    val usernameOrEmail: String = "",
    val password: String = "",
    val rememberMe: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isCreateAccountMode: Boolean = false,
    val createUsername: String = "",
    val createEmail: String = ""
)

class LoginViewModel(private val authRepository: AuthRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onUsernameChange(v: String) { _uiState.value = _uiState.value.copy(usernameOrEmail = v, error = null) }
    fun onPasswordChange(v: String) { _uiState.value = _uiState.value.copy(password = v, error = null) }
    fun onRememberMeChange(v: Boolean) { _uiState.value = _uiState.value.copy(rememberMe = v) }
    fun toggleCreateAccountMode() { _uiState.value = _uiState.value.copy(isCreateAccountMode = !_uiState.value.isCreateAccountMode, error = null) }
    fun onCreateUsernameChange(v: String) { _uiState.value = _uiState.value.copy(createUsername = v) }
    fun onCreateEmailChange(v: String) { _uiState.value = _uiState.value.copy(createEmail = v) }

    fun login(onSuccess: () -> Unit) {
        val state = _uiState.value
        if (state.usernameOrEmail.isBlank() || state.password.isBlank()) {
            _uiState.value = state.copy(error = "Enter username/email and password.")
            return
        }
        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, error = null)
            val result = authRepository.login(state.usernameOrEmail, state.password, state.rememberMe)
            result.onSuccess {
                _uiState.value = _uiState.value.copy(isLoading = false)
                onSuccess()
            }.onFailure {
                _uiState.value = _uiState.value.copy(isLoading = false, error = it.message)
            }
        }
    }

    fun createAccount(onSuccess: () -> Unit) {
        val state = _uiState.value
        if (state.createUsername.isBlank() || state.createEmail.isBlank() || state.password.isBlank()) {
            _uiState.value = state.copy(error = "Fill in all fields to create an account.")
            return
        }
        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, error = null)
            val result = authRepository.register(state.createUsername, state.createEmail, state.password)
            result.onSuccess {
                _uiState.value = _uiState.value.copy(isLoading = false, isCreateAccountMode = false, usernameOrEmail = state.createEmail)
                onSuccess()
            }.onFailure {
                _uiState.value = _uiState.value.copy(isLoading = false, error = it.message)
            }
        }
    }

    fun loginWithGoogle(context: Context, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val serverClientId = context.getString(R.string.google_web_client_id)
            val signInResult = GoogleSignInHelper.signIn(context, serverClientId)
            signInResult.onSuccess { account ->
                val loginResult = authRepository.loginWithGoogle(account.email, account.displayName)
                loginResult.onSuccess {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    onSuccess()
                }.onFailure {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = it.message)
                }
            }.onFailure {
                _uiState.value = _uiState.value.copy(isLoading = false, error = it.message ?: "Google Sign-In was cancelled or failed.")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(viewModel: LoginViewModel, onLoginSuccess: () -> Unit) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.Construction, contentDescription = null, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(8.dp))
        Text("Aarush Construction", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text("Project Management System", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(32.dp))

        if (!state.isCreateAccountMode) {
            OutlinedTextField(
                value = state.usernameOrEmail,
                onValueChange = viewModel::onUsernameChange,
                label = { Text("Username / Email") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.password,
                onValueChange = viewModel::onPasswordChange,
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Checkbox(checked = state.rememberMe, onCheckedChange = viewModel::onRememberMeChange)
                Text("Remember me")
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { /* Forgot password - V1 stub for local auth */ }) { Text("Forgot password?") }
            }
            state.error?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { viewModel.login(onLoginSuccess) },
                enabled = !state.isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.isLoading) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text("Login")
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = viewModel::toggleCreateAccountMode) { Text("Create account") }

            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                HorizontalDivider(modifier = Modifier.weight(1f))
                Text("  OR  ", style = MaterialTheme.typography.labelSmall)
                HorizontalDivider(modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(16.dp))
            val context = LocalContext.current
            OutlinedButton(
                onClick = { viewModel.loginWithGoogle(context, onLoginSuccess) },
                enabled = !state.isLoading,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Sign in with Google") }
        } else {
            OutlinedTextField(
                value = state.createUsername, onValueChange = viewModel::onCreateUsernameChange,
                label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.createEmail, onValueChange = viewModel::onCreateEmailChange,
                label = { Text("Email") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.password, onValueChange = viewModel::onPasswordChange,
                label = { Text("Password") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )
            state.error?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { viewModel.createAccount(onLoginSuccess) },
                enabled = !state.isLoading,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Create account & continue") }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = viewModel::toggleCreateAccountMode) { Text("Back to login") }
        }
    }
}
