package com.parthipan.colorclashcards.ui.auth

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.parthipan.colorclashcards.R
import com.parthipan.colorclashcards.ui.theme.CardBlue
import com.parthipan.colorclashcards.ui.theme.CardGreen
import com.parthipan.colorclashcards.ui.theme.CardRed
import com.parthipan.colorclashcards.ui.theme.CardYellow
import kotlinx.coroutines.delay

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
    onPlayOffline: () -> Unit = {},
    viewModel: AuthViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Staggered entrance animations
    var showLogo by remember { mutableStateOf(false) }
    var showButtons by remember { mutableStateOf(false) }

    val logoScale by animateFloatAsState(
        targetValue = if (showLogo) 1f else 0.6f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "auth_logo_scale"
    )
    val logoAlpha by animateFloatAsState(
        targetValue = if (showLogo) 1f else 0f,
        animationSpec = tween(durationMillis = 600),
        label = "auth_logo_alpha"
    )
    val buttonsAlpha by animateFloatAsState(
        targetValue = if (showButtons) 1f else 0f,
        animationSpec = tween(durationMillis = 500),
        label = "auth_buttons_alpha"
    )
    val buttonsOffset by animateFloatAsState(
        targetValue = if (showButtons) 0f else 30f,
        animationSpec = tween(durationMillis = 500),
        label = "auth_buttons_offset"
    )

    LaunchedEffect(Unit) {
        showLogo = true
        delay(400)
        showButtons = true
    }

    // Google Sign-In launcher
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.handleGoogleSignInResult(result)
    }

    // Check if already signed in
    LaunchedEffect(Unit) {
        viewModel.checkAuthState()
    }

    // Handle state changes
    LaunchedEffect(uiState) {
        when (uiState) {
            is AuthUiState.Success -> {
                onAuthSuccess()
            }
            is AuthUiState.Error -> {
                snackbarHostState.showSnackbar(
                    message = (uiState as AuthUiState.Error).message
                )
                viewModel.resetState()
            }
            else -> {}
        }
    }

    // A2: Animated gradient background — slow color drift
    val gradientTransition = rememberInfiniteTransition(label = "bg_gradient")
    val gradientOffset by gradientTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "gradient_offset"
    )
    val bgColors = listOf(
        CardRed.copy(alpha = 0.8f),
        CardYellow.copy(alpha = 0.6f),
        CardGreen.copy(alpha = 0.6f),
        CardBlue.copy(alpha = 0.8f),
        CardRed.copy(alpha = 0.8f)  // repeat first for smooth wrap
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .drawBehind {
                    val shiftY = gradientOffset * size.height * 0.3f
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = bgColors,
                            startY = -shiftY,
                            endY = size.height + shiftY
                        )
                    )
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Card fan logo (matches splash branding)
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .scale(logoScale)
                        .alpha(logoAlpha),
                    contentAlignment = Alignment.Center
                ) {
                    // Red card (left, -15 degrees)
                    Box(
                        modifier = Modifier
                            .size(58.dp, 84.dp)
                            .graphicsLayer {
                                rotationZ = -15f
                                translationX = -20f
                                transformOrigin = TransformOrigin(0.5f, 1f)
                            }
                            .background(CardRed, RoundedCornerShape(8.dp))
                    )
                    // Green card (center, 0 degrees)
                    Box(
                        modifier = Modifier
                            .size(58.dp, 84.dp)
                            .background(CardGreen, RoundedCornerShape(8.dp))
                    )
                    // Blue card (right, +15 degrees)
                    Box(
                        modifier = Modifier
                            .size(58.dp, 84.dp)
                            .graphicsLayer {
                                rotationZ = 15f
                                translationX = 20f
                                transformOrigin = TransformOrigin(0.5f, 1f)
                            }
                            .background(CardBlue, RoundedCornerShape(8.dp))
                    )
                    // "CCC" overlay
                    Text(
                        text = "CCC",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Color Clash Cards",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.alpha(logoAlpha)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Match colors, play cards, win!",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.alpha(logoAlpha)
                )

                Spacer(modifier = Modifier.height(64.dp))

                // Loading indicator
                if (uiState is AuthUiState.Loading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Signing in...",
                        color = Color.White
                    )
                } else {
                    // Google Sign-In Button with staggered entrance
                    Column(
                        modifier = Modifier
                            .alpha(buttonsAlpha)
                            .offset(y = buttonsOffset.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                    // Google button with press-scale + glow shadow
                    val googleInteraction = remember { MutableInteractionSource() }
                    val googlePressed by googleInteraction.collectIsPressedAsState()
                    val googleScale by animateFloatAsState(
                        targetValue = if (googlePressed) 0.96f else 1f,
                        animationSpec = spring(stiffness = Spring.StiffnessHigh),
                        label = "google_btn_scale"
                    )

                    Button(
                        onClick = {
                            viewModel.setLoading()
                            val webClientId = context.getString(R.string.default_web_client_id)
                            val googleSignInClient = GoogleSignIn.getClient(
                                context,
                                viewModel.getGoogleSignInOptions(webClientId)
                            )
                            // Sign out first to ensure account picker shows
                            googleSignInClient.signOut().addOnCompleteListener {
                                googleSignInLauncher.launch(googleSignInClient.signInIntent)
                            }
                        },
                        interactionSource = googleInteraction,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .scale(googleScale)
                            .shadow(
                                elevation = 12.dp,
                                shape = RoundedCornerShape(28.dp),
                                ambientColor = Color.White.copy(alpha = 0.3f),
                                spotColor = Color.White.copy(alpha = 0.3f)
                            ),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White
                        ),
                        shape = RoundedCornerShape(28.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            // Google "G" placeholder
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(
                                        color = Color(0xFF4285F4),
                                        shape = RoundedCornerShape(4.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "G",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Continue with Google",
                                color = Color.DarkGray,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Play Offline Button with press-scale
                    val offlineInteraction = remember { MutableInteractionSource() }
                    val offlinePressed by offlineInteraction.collectIsPressedAsState()
                    val offlineScale by animateFloatAsState(
                        targetValue = if (offlinePressed) 0.96f else 1f,
                        animationSpec = spring(stiffness = Spring.StiffnessHigh),
                        label = "offline_btn_scale"
                    )

                    OutlinedButton(
                        onClick = onPlayOffline,
                        interactionSource = offlineInteraction,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .scale(offlineScale),
                        border = BorderStroke(2.dp, Color.White),
                        shape = RoundedCornerShape(28.dp)
                    ) {
                        Text(
                            text = "Play Offline",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "Sign in with Google to play online.\nPlay offline against bots — no account needed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                    } // end animated Column
                }
            }
        }
    }
}
