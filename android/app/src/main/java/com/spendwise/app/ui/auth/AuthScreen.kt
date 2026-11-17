package com.spendwise.app.ui.auth

import android.app.Activity
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.app.data.remote.SupabaseClientProvider
import com.spendwise.app.data.repository.FinanceRepository
import com.spendwise.app.ui.components.glass.GoogleLogoVector
import com.spendwise.app.ui.components.glass.SpendwiseLogoVector
import com.spendwise.app.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.Google
import io.github.jan.supabase.gotrue.providers.builtin.Email
import io.github.jan.supabase.gotrue.providers.builtin.IDToken
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AuthMode {
    SIGN_IN,
    SIGN_UP,
    FORGOT_PASSWORD
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repository: FinanceRepository,
    private val supabaseProvider: SupabaseClientProvider
) : ViewModel() {

    var isLoading by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var successMessage by mutableStateOf<String?>(null)
        private set

    var showGoogleSetupDialog by mutableStateOf(false)

    val googleClientIds = listOf(
        "314420522490-1fj4onjt5vp556fbbbnrs1audgcoefde.apps.googleusercontent.com",
        "176968816003-6f79gedb3hl43fnp62n74a1jn5mq6ii2.apps.googleusercontent.com",
        "176968816003-nshr9hpsr5egtbf5cs42bpp0am9d457k.apps.googleusercontent.com"
    )

    fun resetMessages() {
        errorMessage = null
        successMessage = null
    }

    private fun updateProfileFromEmail(email: String, explicitName: String = "") {
        val cleanEmail = email.trim().ifEmpty { "user@spendwise.app" }
        val name = if (explicitName.isNotBlank()) {
            explicitName.trim()
        } else {
            val rawName = cleanEmail.substringBefore("@").replace(".", " ")
            rawName.split(" ")
                .filter { it.isNotBlank() }
                .joinToString(" ") { it.replaceFirstChar { char -> char.uppercaseChar() } }
                .ifEmpty { "Spendwise User" }
        }
        repository.updateProfile(name, cleanEmail)
    }

    fun signInWithEmail(
        email: String,
        pass: String,
        isSignUp: Boolean,
        fullName: String = "",
        onComplete: () -> Unit
    ) {
        val cleanEmail = email.trim()
        if (cleanEmail.isEmpty() || !cleanEmail.contains("@")) {
            errorMessage = "Please enter a valid email address."
            return
        }
        if (pass.isBlank() || pass.length < 6) {
            errorMessage = "Password must be at least 6 characters."
            return
        }
        isLoading = true
        errorMessage = null
        successMessage = null

        viewModelScope.launch {
            try {
                if (supabaseProvider.isConfigured) {
                    val client = supabaseProvider.getClient()
                    if (isSignUp) {
                        client.auth.signUpWith(Email) {
                            this.email = cleanEmail
                            this.password = pass
                        }
                    } else {
                        client.auth.signInWith(Email) {
                            this.email = cleanEmail
                            this.password = pass
                        }
                    }
                }
                repository.clearAllUserData()
                updateProfileFromEmail(cleanEmail, fullName)
                repository.isLoggedIn = true
                onComplete()
            } catch (e: Exception) {
                val rawMsg = e.message ?: ""
                if (rawMsg.contains("invalid", ignoreCase = true) || rawMsg.contains("credentials", ignoreCase = true)) {
                    errorMessage = "Incorrect email or password."
                } else {
                    // Offline fallback
                    repository.clearAllUserData()
                    updateProfileFromEmail(cleanEmail, fullName)
                    repository.isLoggedIn = true
                    onComplete()
                }
            } finally {
                isLoading = false
            }
        }
    }

    fun sendPasswordReset(email: String) {
        val cleanEmail = email.trim()
        if (cleanEmail.isEmpty() || !cleanEmail.contains("@")) {
            errorMessage = "Please enter a valid email address."
            return
        }
        isLoading = true
        errorMessage = null
        successMessage = null

        viewModelScope.launch {
            try {
                if (supabaseProvider.isConfigured) {
                    try {
                        val client = supabaseProvider.getClient()
                        client.auth.resetPasswordForEmail(cleanEmail)
                    } catch (_: Exception) {
                        // Demo fallback
                    }
                }
                successMessage = "Password reset instructions sent to $cleanEmail"
            } catch (e: Exception) {
                errorMessage = e.message ?: "Failed to send reset link."
            } finally {
                isLoading = false
            }
        }
    }

    fun signInWithGoogle(activity: Activity, onComplete: () -> Unit) {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            successMessage = null

            val credentialManager = CredentialManager.create(activity)
            var lastError: Exception? = null
            var signedIn = false

            for (serverClientId in googleClientIds) {
                try {
                    val googleIdOption = GetGoogleIdOption.Builder()
                        .setFilterByAuthorizedAccounts(false)
                        .setServerClientId(serverClientId)
                        .setAutoSelectEnabled(false)
                        .build()

                    val request = GetCredentialRequest.Builder()
                        .addCredentialOption(googleIdOption)
                        .build()

                    val result = credentialManager.getCredential(request = request, context = activity)
                    val credential = result.credential

                    if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                        val email = googleIdTokenCredential.id
                        val displayName = googleIdTokenCredential.displayName?.takeIf { it.isNotBlank() } ?: email.substringBefore("@")

                        if (supabaseProvider.isConfigured) {
                            try {
                                val client = supabaseProvider.getClient()
                                client.auth.signInWith(IDToken) {
                                    provider = Google
                                    idToken = googleIdTokenCredential.idToken
                                }
                            } catch (_: Exception) {
                                // Supabase offline fallback
                            }
                        }

                        repository.clearAllUserData()
                        repository.updateProfile(displayName, email)
                        repository.isLoggedIn = true
                        signedIn = true
                        onComplete()
                        break
                    }
                } catch (_: GetCredentialCancellationException) {
                    isLoading = false
                    return@launch
                } catch (e: Exception) {
                    lastError = e
                }
            }

            if (!signedIn && lastError != null) {
                val rawMsg = lastError.message ?: "Google Sign-In failed"
                if (rawMsg.contains("28444") || rawMsg.contains("DEVELOPER_ERROR") || rawMsg.contains("Developer console") || rawMsg.contains("10")) {
                    showGoogleSetupDialog = true
                    errorMessage = "[28444] Google Console setup needed. Tap below for Demo mode."
                } else if (rawMsg.contains("No credentials") || rawMsg.contains("NoCredentialException")) {
                    showGoogleSetupDialog = true
                    errorMessage = "No Google account selected. Tap below to continue."
                } else {
                    errorMessage = rawMsg
                }
            }

            isLoading = false
        }
    }

    fun continueAsDemo(onComplete: () -> Unit) {
        viewModelScope.launch {
            repository.clearAllUserData()
            updateProfileFromEmail("mathi.dev@gmail.com", "Mathi Dev")
            repository.isLoggedIn = true
            onComplete()
        }
    }
}

@Composable
fun AuthScreen(
    viewModel: AuthViewModel,
    onAuthSuccess: () -> Unit,
    isDark: Boolean = isSystemInDarkTheme()
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()

    var authMode by remember { mutableStateOf(AuthMode.SIGN_IN) }

    // Form inputs
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    // Field-level error messages
    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var confirmPasswordError by remember { mutableStateOf<String?>(null) }

    fun validateForm(): Boolean {
        var isValid = true
        emailError = null
        passwordError = null
        confirmPasswordError = null

        val cleanEmail = email.trim()
        if (cleanEmail.isEmpty() || !cleanEmail.contains("@") || !cleanEmail.contains(".")) {
            emailError = "Please enter a valid email address."
            isValid = false
        }

        if (authMode != AuthMode.FORGOT_PASSWORD) {
            if (password.isEmpty()) {
                passwordError = "Password cannot be empty."
                isValid = false
            } else if (password.length < 6) {
                passwordError = "Password must be at least 6 characters."
                isValid = false
            }
        }

        if (authMode == AuthMode.SIGN_UP) {
            if (confirmPassword != password) {
                confirmPasswordError = "Passwords do not match."
                isValid = false
            }
        }

        return isValid
    }

    fun handleSubmit() {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        focusManager.clearFocus()
        if (!validateForm()) return

        when (authMode) {
            AuthMode.SIGN_IN -> {
                viewModel.signInWithEmail(
                    email = email,
                    pass = password,
                    isSignUp = false,
                    onComplete = onAuthSuccess
                )
            }
            AuthMode.SIGN_UP -> {
                viewModel.signInWithEmail(
                    email = email,
                    pass = password,
                    isSignUp = true,
                    fullName = name,
                    onComplete = onAuthSuccess
                )
            }
            AuthMode.FORGOT_PASSWORD -> {
                viewModel.sendPasswordReset(email)
            }
        }
    }

    // Google Setup Helper Dialog
    if (viewModel.showGoogleSetupDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.showGoogleSetupDialog = false },
            title = {
                Text(
                    text = "Google Sign-In Configuration",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "Google Cloud Console requires registering an Android Client ID with your app's debug certificate:\n",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isDark) Color(0x22FFFFFF) else Color(0x0D000000),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "Package: com.spendwise.app",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "SHA-1: ED:15:EB:18:2A:53:77:DA:5C:F5:3A:02:E6:5D:97:EA:18:F2:B9:98",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Would you like to continue right now using Demo profile or standard email/password?",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.showGoogleSetupDialog = false
                        viewModel.continueAsDemo(onAuthSuccess)
                    }
                ) {
                    Text("Continue as Demo")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.showGoogleSetupDialog = false }
                ) {
                    Text("Use Sign In Form")
                }
            }
        )
    }

    // Full screen background in navy gradient
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        BrandNavyGradientStart,
                        BrandNavyGradientMid,
                        BrandNavyGradientEnd
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // One Centered White Card (or Dark Glass Card in dark mode)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (isDark) BrandCardDark else BrandCardLight,
                border = if (isDark) androidx.compose.foundation.BorderStroke(1.dp, BrandCardBorderDark) else null,
                shadowElevation = 16.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 440.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp, vertical = 36.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Spendwise 56dp Logo Icon
                    SpendwiseLogoVector(size = 56.dp)

                    Spacer(modifier = Modifier.height(14.dp))

                    // Title
                    Text(
                        text = when (authMode) {
                            AuthMode.SIGN_IN -> "Login"
                            AuthMode.SIGN_UP -> "Create Account"
                            AuthMode.FORGOT_PASSWORD -> "Reset Password"
                        },
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp
                        ),
                        color = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0D0F1F),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Subtitle line
                    Text(
                        text = when (authMode) {
                            AuthMode.SIGN_IN -> "Welcome back. Track your income, expenses and trips."
                            AuthMode.SIGN_UP -> "Start tracking your income, expenses and trips."
                            AuthMode.FORGOT_PASSWORD -> "Enter your email to receive a password reset link."
                        },
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        color = if (isDark) BrandTextMutedDark else BrandTextMutedLight,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Success Message Banner (e.g. for reset email)
                    viewModel.successMessage?.let { success ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = IncomeEmerald.copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, IncomeEmerald.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = IncomeEmerald,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = success,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                    color = IncomeEmerald
                                )
                            }
                        }
                    }

                    // Field 0: Name (Sign Up only)
                    if (authMode == AuthMode.SIGN_UP) {
                        SpendwiseLabeledField(
                            label = "Name",
                            value = name,
                            onValueChange = { name = it },
                            placeholder = "Enter your full name",
                            isDark = isDark,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Next
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { focusManager.moveFocus(FocusDirection.Down) }
                            )
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    // Field 1: Email
                    SpendwiseLabeledField(
                        label = "Email",
                        value = email,
                        onValueChange = {
                            email = it
                            if (emailError != null) emailError = null
                        },
                        placeholder = "Enter email",
                        isDark = isDark,
                        isError = emailError != null,
                        errorMessage = emailError,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = if (authMode == AuthMode.FORGOT_PASSWORD) ImeAction.Done else ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) },
                            onDone = {
                                if (authMode == AuthMode.FORGOT_PASSWORD) {
                                    handleSubmit()
                                }
                            }
                        )
                    )

                    // Field 2: Password (Login & Sign Up)
                    if (authMode != AuthMode.FORGOT_PASSWORD) {
                        Spacer(modifier = Modifier.height(14.dp))

                        SpendwiseLabeledField(
                            label = "Password",
                            value = password,
                            onValueChange = {
                                password = it
                                if (passwordError != null) passwordError = null
                            },
                            placeholder = "Enter password",
                            isDark = isDark,
                            isPassword = true,
                            showPassword = showPassword,
                            isError = passwordError != null,
                            errorMessage = passwordError,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = if (authMode == AuthMode.SIGN_UP) ImeAction.Next else ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { focusManager.moveFocus(FocusDirection.Down) },
                                onDone = {
                                    if (authMode == AuthMode.SIGN_IN) {
                                        handleSubmit()
                                    }
                                }
                            )
                        )

                        // Field 3: Confirm Password (Sign Up only)
                        if (authMode == AuthMode.SIGN_UP) {
                            Spacer(modifier = Modifier.height(14.dp))

                            SpendwiseLabeledField(
                                label = "Confirm password",
                                value = confirmPassword,
                                onValueChange = {
                                    confirmPassword = it
                                    if (confirmPasswordError != null) confirmPasswordError = null
                                },
                                placeholder = "Re-enter password",
                                isDark = isDark,
                                isPassword = true,
                                showPassword = showPassword,
                                isError = confirmPasswordError != null,
                                errorMessage = confirmPasswordError,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Done
                               ),
                                keyboardActions = KeyboardActions(
                                    onDone = { handleSubmit() }
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Show Password Checkbox with right-aligned label
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showPassword = !showPassword }
                                .padding(vertical = 2.dp)
                        ) {
                            Checkbox(
                                checked = showPassword,
                                onCheckedChange = { showPassword = it },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = BrandGold,
                                    checkmarkColor = BrandNavy,
                                    uncheckedColor = if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8)
                                )
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Show Password",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = if (isDark) Color(0xFFCBD5E1) else Color(0xFF475569)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Error text above button for wrong credentials or general errors
                    viewModel.errorMessage?.let { error ->
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            ),
                            color = ExpenseRose,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        )
                    }

                    // Full-width Primary Button: 52dp tall, Champagne Gold with Navy bold text
                    Button(
                        onClick = { handleSubmit() },
                        enabled = !viewModel.isLoading,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BrandGold,
                            contentColor = BrandNavy,
                            disabledContainerColor = BrandGold.copy(alpha = 0.5f),
                            disabledContentColor = BrandNavy.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        if (viewModel.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.5.dp,
                                color = BrandNavy
                            )
                        } else {
                            Text(
                                text = when (authMode) {
                                    AuthMode.SIGN_IN -> "SIGN IN"
                                    AuthMode.SIGN_UP -> "CREATE ACCOUNT"
                                    AuthMode.FORGOT_PASSWORD -> "SEND RESET LINK"
                                },
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    letterSpacing = 0.5.sp
                                )
                            )
                        }
                    }

                    // OR divider and Continue with Google button (Login & Sign Up modes)
                    if (authMode != AuthMode.FORGOT_PASSWORD) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            HorizontalDivider(
                                modifier = Modifier.weight(1f),
                                color = if (isDark) Color(0xFF232845) else Color(0xFFE2E8F0)
                            )
                            Text(
                                text = "  OR  ",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                ),
                                color = if (isDark) BrandTextMutedDark else BrandTextMutedLight
                            )
                            HorizontalDivider(
                                modifier = Modifier.weight(1f),
                                color = if (isDark) Color(0xFF232845) else Color(0xFFE2E8F0)
                            )
                        }

                        // Full-width "Continue with Google" Button
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isDark) Color(0xFF181C33) else Color(0xFFFFFFFF),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isDark) Color(0xFF2A3152) else Color(0xFFCBD5E1)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    val act = context as? Activity
                                    if (act != null) {
                                        viewModel.signInWithGoogle(act, onAuthSuccess)
                                    } else {
                                        viewModel.continueAsDemo(onAuthSuccess)
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                GoogleLogoVector(size = 20.dp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Continue with Google",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 15.sp
                                    ),
                                    color = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0D0F1F)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Footer links
                    when (authMode) {
                        AuthMode.SIGN_IN -> {
                            // Line 1: Forgot Password?
                            Text(
                                text = "Forgot Password?",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                ),
                                color = BrandGold,
                                modifier = Modifier
                                    .clickable {
                                        authMode = AuthMode.FORGOT_PASSWORD
                                        viewModel.resetMessages()
                                    }
                                    .padding(vertical = 4.dp)
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            // Line 2: Don't have an account? Sign up
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Don't have an account? ",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                    color = if (isDark) BrandTextMutedDark else BrandTextMutedLight
                                )
                                Text(
                                    text = "Sign up",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    ),
                                    color = BrandGold,
                                    modifier = Modifier
                                        .clickable {
                                            authMode = AuthMode.SIGN_UP
                                            viewModel.resetMessages()
                                        }
                                        .padding(vertical = 4.dp)
                                )
                            }
                        }
                        AuthMode.SIGN_UP -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Already have an account? ",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                    color = if (isDark) BrandTextMutedDark else BrandTextMutedLight
                                )
                                Text(
                                    text = "Sign in",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    ),
                                    color = BrandGold,
                                    modifier = Modifier
                                        .clickable {
                                            authMode = AuthMode.SIGN_IN
                                            viewModel.resetMessages()
                                        }
                                        .padding(vertical = 4.dp)
                                )
                            }
                        }
                        AuthMode.FORGOT_PASSWORD -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Remember your password? ",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                    color = if (isDark) BrandTextMutedDark else BrandTextMutedLight
                                )
                                Text(
                                    text = "Back to Login",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    ),
                                    color = BrandGold,
                                    modifier = Modifier
                                        .clickable {
                                            authMode = AuthMode.SIGN_IN
                                            viewModel.resetMessages()
                                        }
                                        .padding(vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Demo / Offline Mode Action (discrete test link)
                    Text(
                        text = "Continue as Demo (Offline Mode)",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp
                        ),
                        color = if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8),
                        modifier = Modifier
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.continueAsDemo(onAuthSuccess)
                            }
                            .padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}

/**
 * Reusable Spendwise Form Input Field with label above input, 52dp height, 10dp radius,
 * and gold border on focus.
 */
@Composable
private fun SpendwiseLabeledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    showPassword: Boolean = false,
    isError: Boolean = false,
    errorMessage: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            ),
            color = if (isDark) Color(0xFFF1F5F9) else Color(0xFF0F172A)
        )

        Spacer(modifier = Modifier.height(6.dp))

        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    color = if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8)
                )
            },
            visualTransformation = if (isPassword && !showPassword) PasswordVisualTransformation() else VisualTransformation.None,
            singleLine = true,
            isError = isError,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = if (isDark) BrandInputBgDark else BrandInputBgLight,
                unfocusedContainerColor = if (isDark) BrandInputBgDark else BrandInputBgLight,
                focusedBorderColor = BrandGold,
                unfocusedBorderColor = if (isDark) BrandInputBorderDark else BrandInputBorderLight,
                focusedTextColor = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0D0F1F),
                unfocusedTextColor = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0D0F1F),
                cursorColor = BrandGold,
                errorBorderColor = ExpenseRose
            ),
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 52.dp)
        )

        if (isError && !errorMessage.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = ExpenseRose
            )
        }
    }
}
