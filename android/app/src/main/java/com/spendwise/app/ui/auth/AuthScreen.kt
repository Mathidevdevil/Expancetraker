package com.spendwise.app.ui.auth

import android.app.Activity
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.spendwise.app.ui.components.glass.Login3DScene
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
    var authMode by mutableStateOf(AuthMode.SIGN_IN)
    var showEmailConfirmationHelp by mutableStateOf(false)
    var pendingConfirmationEmail by mutableStateOf("")

    var showGoogleSetupDialog by mutableStateOf(false)

    val googleClientIds = listOf(
        "314420522490-1fj4onjt5vp556fbbbnrs1audgcoefde.apps.googleusercontent.com",
        "176968816003-6f79gedb3hl43fnp62n74a1jn5mq6ii2.apps.googleusercontent.com",
        "176968816003-nshr9hpsr5egtbf5cs42bpp0am9d457k.apps.googleusercontent.com"
    )

    fun resetMessages() {
        errorMessage = null
        successMessage = null
        showEmailConfirmationHelp = false
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
        showEmailConfirmationHelp = false

        viewModelScope.launch {
            try {
                if (supabaseProvider.isConfigured) {
                    val client = supabaseProvider.getClient()
                    if (isSignUp) {
                        try {
                            client.auth.signUpWith(Email) {
                                this.email = cleanEmail
                                this.password = pass
                            }
                        } catch (signupEx: Exception) {
                            val msg = signupEx.message ?: ""
                            if (msg.contains("already registered", ignoreCase = true) || 
                                msg.contains("User already registered", ignoreCase = true) ||
                                msg.contains("already exists", ignoreCase = true)) {
                                errorMessage = "An account with this email already exists. Please Sign In below."
                                authMode = AuthMode.SIGN_IN
                                return@launch
                            } else {
                                throw signupEx
                            }
                        }

                        // Check if session was granted immediately (when Confirm Email is OFF in Supabase)
                        val session = client.auth.currentSessionOrNull()
                        val authUser = client.auth.currentUserOrNull()

                        if (session != null && authUser != null) {
                            // Instant login without confirmation requirement
                            repository.authUserId = authUser.id
                            repository.syncFromCloud()
                            val prevEmail = repository.userProfile.value.email
                            if (prevEmail.isNotBlank() && prevEmail != cleanEmail && prevEmail != "user@spendwise.app") {
                                repository.clearAllUserData()
                            }
                            updateProfileFromEmail(cleanEmail, fullName)
                            repository.isLoggedIn = true
                            onComplete()
                            return@launch
                        } else {
                            // Supabase has 'Confirm email' enabled
                            pendingConfirmationEmail = cleanEmail
                            showEmailConfirmationHelp = true
                            successMessage = "Account created! Please check your email inbox ($cleanEmail) and click the confirmation link to activate your account, then sign in."
                            authMode = AuthMode.SIGN_IN
                            return@launch
                        }
                    } else {
                        // Sign In with email & password
                        client.auth.signInWith(Email) {
                            this.email = cleanEmail
                            this.password = pass
                        }
                        val authUser = client.auth.currentUserOrNull()
                        if (authUser != null) {
                            repository.authUserId = authUser.id
                        }
                        repository.syncFromCloud()
                        val prevEmail = repository.userProfile.value.email
                        if (prevEmail.isNotBlank() && prevEmail != cleanEmail && prevEmail != "user@spendwise.app") {
                            repository.clearAllUserData()
                        }
                        updateProfileFromEmail(cleanEmail, fullName)
                        repository.isLoggedIn = true
                        onComplete()
                        return@launch
                    }
                }

                // Supabase not configured fallback
                val prevEmail = repository.userProfile.value.email
                if (isSignUp && prevEmail.isNotBlank() && prevEmail != cleanEmail && prevEmail != "user@spendwise.app") {
                    repository.clearAllUserData()
                }
                updateProfileFromEmail(cleanEmail, fullName)
                repository.isLoggedIn = true
                onComplete()
            } catch (e: Exception) {
                val rawMsg = e.message ?: ""
                android.util.Log.e("AuthScreen", "Auth error: $rawMsg", e)
                if (rawMsg.contains("invalid", ignoreCase = true) || rawMsg.contains("credentials", ignoreCase = true)) {
                    errorMessage = "Incorrect email or password. Please verify and try again."
                } else if (rawMsg.contains("email not confirmed", ignoreCase = true) || rawMsg.contains("email_not_confirmed", ignoreCase = true)) {
                    pendingConfirmationEmail = cleanEmail
                    showEmailConfirmationHelp = true
                    errorMessage = "Email not confirmed! Please check your inbox ($cleanEmail) and tap the confirmation link, or disable 'Confirm email' in Supabase dashboard."
                } else if (rawMsg.contains("already registered", ignoreCase = true) || rawMsg.contains("already exists", ignoreCase = true)) {
                    errorMessage = "This email is already registered. Please Sign In."
                    authMode = AuthMode.SIGN_IN
                } else if (rawMsg.contains("rate limit", ignoreCase = true)) {
                    errorMessage = "Too many attempts. Please wait a minute and try again."
                } else {
                    errorMessage = rawMsg.ifBlank { "Authentication failed. Please check your credentials and internet connection." }
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
                                val authUser = client.auth.currentUserOrNull()
                                if (authUser != null) {
                                    repository.authUserId = authUser.id
                                }
                                repository.syncFromCloud()
                            } catch (_: Exception) {
                                // Supabase offline fallback
                            }
                        }

                        val prevEmail = repository.userProfile.value.email
                        if (prevEmail.isNotBlank() && prevEmail != email && prevEmail != "user@spendwise.app") {
                            repository.clearAllUserData()
                        }
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

    val authMode = viewModel.authMode

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
                                text = "SHA-1: BD:E4:51:8E:2B:FC:62:18:83:AE:33:D4:0D:03:26:B3:C2:BD:46:62",
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

    // Royal Plum 3D Perspective Login Scene
    Login3DScene(
        scrollModifier = Modifier.verticalScroll(scrollState)
    ) {
                    // Title (Floating 56dp Spendwise "S" Logo sits centered half above top edge)
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
                        color = Color(0xFFFBF5EA),
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
                        color = Color(0xFFC4B8A4),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Success Message Banner (e.g. for reset email)
                    viewModel.successMessage?.let { success ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = IncomeDark.copy(alpha = 0.14f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, IncomeDark.copy(alpha = 0.45f)),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = IncomeDark,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = success,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                    color = IncomeDark
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
                                .heightIn(min = 48.dp)
                                .clickable { showPassword = !showPassword }
                                .padding(vertical = 2.dp)
                        ) {
                            Checkbox(
                                checked = showPassword,
                                onCheckedChange = { showPassword = it },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = Color(0xFFD4AF6A),
                                    checkmarkColor = Color(0xFF2A0F1A),
                                    uncheckedColor = Color(0xFFC4B8A4)
                                )
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Show Password",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = Color(0xFFC4B8A4)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Error text above button for wrong credentials or general errors
                    viewModel.errorMessage?.let { error ->
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            ),
                            color = Color(0xFFF1887A),
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        )
                    }

                    // Email Confirmation Guidance Banner
                    if (viewModel.showEmailConfirmationHelp) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xB314060C),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD4AF6A).copy(alpha = 0.45f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 14.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = Color(0xFFD4AF6A),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Email Confirmation Required",
                                        fontFamily = SfPro,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = Color(0xFFF3DBA0)
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Supabase sent an activation email to your address. Please open your email inbox and tap the link to activate your account, then sign in.\n\n💡 Admin Tip: To skip email verification, turn off 'Confirm email' in Supabase Dashboard -> Authentication -> Providers -> Email.",
                                    fontFamily = SfPro,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    color = Color(0xFFFBF5EA)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = { viewModel.continueAsDemo(onAuthSuccess) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFD4AF6A).copy(alpha = 0.22f),
                                        contentColor = Color(0xFFF3DBA0)
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(38.dp)
                                ) {
                                    Text(
                                        text = "Continue in Offline / Demo Mode",
                                        fontFamily = SfPro,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }

                    // Full-width Primary Button: Physical 3D Gold Key (52dp face + 6dp extruded base)
                    val primaryInteraction = remember { MutableInteractionSource() }
                    val isPrimaryPressed by primaryInteraction.collectIsPressedAsState()
                    val primaryPressOffset by animateDpAsState(
                        targetValue = if (isPrimaryPressed && !viewModel.isLoading) 4.dp else 0.dp,
                        animationSpec = spring(stiffness = 700f),
                        label = "GoldKeyPress"
                    )
                    val goldKeyShape = RoundedCornerShape(10.dp)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp)
                    ) {
                        // 6dp Extruded Darker Gold Base + Soft Floor Shadow
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(52.dp)
                                .shadow(
                                    elevation = 10.dp,
                                    shape = goldKeyShape,
                                    spotColor = Color(0xCC060103),
                                    ambientColor = Color(0x80060103)
                                )
                                .clip(goldKeyShape)
                                .background(Color(0xFF8A6420))
                        )

                        // Raised Physical Gold Keycap
                        Box(
                            modifier = Modifier
                                .offset(y = primaryPressOffset)
                                .fillMaxWidth()
                                .height(52.dp)
                                .clip(goldKeyShape)
                                .background(
                                    Brush.verticalGradient(
                                        colors = if (viewModel.isLoading) {
                                            listOf(
                                                Color(0xFFF3DBA0).copy(alpha = 0.65f),
                                                Color(0xFFD4AF6A).copy(alpha = 0.65f),
                                                Color(0xFFB68A3E).copy(alpha = 0.65f)
                                            )
                                        } else {
                                            listOf(
                                                Color(0xFFF3DBA0),
                                                Color(0xFFD4AF6A),
                                                Color(0xFFB68A3E)
                                            )
                                        }
                                    )
                                )
                                .border(
                                    width = 1.dp,
                                    color = Color(0xFFF3DBA0).copy(alpha = 0.85f),
                                    shape = goldKeyShape
                                )
                                .clickable(
                                    interactionSource = primaryInteraction,
                                    indication = null,
                                    enabled = !viewModel.isLoading,
                                    onClick = { handleSubmit() }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (viewModel.isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    strokeWidth = 2.5.dp,
                                    color = Color(0xFF2A0F1A)
                                )
                            } else {
                                Text(
                                    text = when (authMode) {
                                        AuthMode.SIGN_IN -> "SIGN IN"
                                        AuthMode.SIGN_UP -> "CREATE ACCOUNT"
                                        AuthMode.FORGOT_PASSWORD -> "SEND RESET LINK"
                                    },
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 15.sp,
                                        letterSpacing = 0.6.sp
                                    ),
                                    color = Color(0xFF2A0F1A)
                                )
                            }
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
                                color = Color(0xFFD4AF6A).copy(alpha = 0.25f)
                            )
                            Text(
                                text = "  OR  ",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                ),
                                color = Color(0xFFC4B8A4)
                            )
                            HorizontalDivider(
                                modifier = Modifier.weight(1f),
                                color = Color(0xFFD4AF6A).copy(alpha = 0.25f)
                            )
                        }

                        // Full-width "Continue with Google" Translucent Plum Glass Key with Gold Outline
                        val googleInteraction = remember { MutableInteractionSource() }
                        val isGooglePressed by googleInteraction.collectIsPressedAsState()
                        val googlePressOffset by animateDpAsState(
                            targetValue = if (isGooglePressed) 3.dp else 0.dp,
                            animationSpec = spring(stiffness = 700f),
                            label = "GoogleKeyPress"
                        )
                        val googleShape = RoundedCornerShape(10.dp)

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(55.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .clip(googleShape)
                                    .background(Color(0xFF100409))
                            )

                            Surface(
                                shape = googleShape,
                                color = Color(0xBF2A0F1A),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.5.dp,
                                    Color(0xFFD4AF6A).copy(alpha = 0.65f)
                                ),
                                modifier = Modifier
                                    .offset(y = googlePressOffset)
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .clickable(
                                        interactionSource = googleInteraction,
                                        indication = null
                                    ) {
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
                                        color = Color(0xFFFBF5EA)
                                    )
                                }
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
                                color = Color(0xFFD4AF6A),
                                modifier = Modifier
                                    .clickable {
                                        viewModel.authMode = AuthMode.FORGOT_PASSWORD
                                        viewModel.resetMessages()
                                    }
                                    .padding(vertical = 8.dp, horizontal = 12.dp)
                            )

                            // Line 2: Don't have an account? Sign up
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Don't have an account? ",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                    color = Color(0xFFC4B8A4)
                                )
                                Text(
                                    text = "Sign up",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    ),
                                    color = Color(0xFFD4AF6A),
                                    modifier = Modifier
                                        .clickable {
                                            viewModel.authMode = AuthMode.SIGN_UP
                                            viewModel.resetMessages()
                                        }
                                        .padding(vertical = 8.dp, horizontal = 4.dp)
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
                                    color = Color(0xFFC4B8A4)
                                )
                                Text(
                                    text = "Sign in",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    ),
                                    color = Color(0xFFD4AF6A),
                                    modifier = Modifier
                                        .clickable {
                                            viewModel.authMode = AuthMode.SIGN_IN
                                            viewModel.resetMessages()
                                        }
                                        .padding(vertical = 8.dp, horizontal = 4.dp)
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
                                    color = Color(0xFFC4B8A4)
                                )
                                Text(
                                    text = "Back to Login",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    ),
                                    color = Color(0xFFD4AF6A),
                                    modifier = Modifier
                                        .clickable {
                                            viewModel.authMode = AuthMode.SIGN_IN
                                            viewModel.resetMessages()
                                        }
                                        .padding(vertical = 8.dp, horizontal = 4.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Demo / Offline Mode Action (discrete test link, WCAG AA on plum glass)
                    Text(
                        text = "Continue as Demo (Offline Mode)",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp
                        ),
                        color = Color(0xFFC4B8A4),
                        modifier = Modifier
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.continueAsDemo(onAuthSuccess)
                            }
                            .padding(vertical = 10.dp, horizontal = 12.dp)
                    )
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
    var isFocused by remember { mutableStateOf(false) }
    val fieldShape = RoundedCornerShape(10.dp)

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            ),
            color = Color(0xFFFBF5EA)
        )

        Spacer(modifier = Modifier.height(6.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isFocused && !isError) {
                        Modifier.border(
                            width = 2.dp,
                            color = Color(0xFFD4AF6A).copy(alpha = 0.35f),
                            shape = RoundedCornerShape(11.dp)
                        )
                    } else {
                        Modifier
                    }
                )
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        color = Color(0xFFC4B8A4).copy(alpha = 0.78f)
                    )
                },
                visualTransformation = if (isPassword && !showPassword) PasswordVisualTransformation() else VisualTransformation.None,
                singleLine = true,
                isError = isError,
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                shape = fieldShape,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0xEB14060C),
                    unfocusedContainerColor = Color(0xEB14060C),
                    errorContainerColor = Color(0xEB14060C),
                    focusedBorderColor = Color(0xFFD4AF6A),
                    unfocusedBorderColor = Color(0xFF8F8272).copy(alpha = 0.55f),
                    focusedTextColor = Color(0xFFFBF5EA),
                    unfocusedTextColor = Color(0xFFFBF5EA),
                    cursorColor = Color(0xFFD4AF6A),
                    errorBorderColor = Color(0xFFF1887A)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 52.dp)
                    .onFocusChanged { isFocused = it.isFocused }
                    .drawWithContent {
                        drawContent()
                        // Subtle recessed inset top shadow inside the field
                        val r = 10.dp.toPx()
                        drawLine(
                            color = Color(0xAA050103),
                            start = Offset(r, 2.dp.toPx()),
                            end = Offset(size.width - r, 2.dp.toPx()),
                            strokeWidth = 2.5.dp.toPx()
                        )
                        drawRoundRect(
                            color = Color(0x28050103),
                            topLeft = Offset(1.dp.toPx(), 1.dp.toPx()),
                            size = Size(size.width - 2.dp.toPx(), size.height - 2.dp.toPx()),
                            cornerRadius = CornerRadius(r, r),
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
            )
        }

        if (isError && !errorMessage.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = Color(0xFFF1887A)
            )
        }
    }
}
