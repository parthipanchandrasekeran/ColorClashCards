package com.parthipan.colorclashcards.ui.privacy

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.parthipan.colorclashcards.ui.theme.CardBlue

/**
 * Privacy Policy screen explaining data usage.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen(
    onBackClick: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy Policy") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CardBlue,
                    titleContentColor = MaterialTheme.colorScheme.surface,
                    navigationIconContentColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text = "Last updated: January 2025",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            PrivacySection(
                title = "Overview",
                content = "Color Clash Cards is a casual card game that respects your privacy. " +
                        "We collect minimal data necessary to provide online multiplayer features. " +
                        "This policy explains what data we collect and how we use it."
            )

            Spacer(modifier = Modifier.height(16.dp))

            PrivacySection(
                title = "Information We Collect",
                content = "When you sign in with Google, we receive your Google account ID, " +
                        "display name, and profile picture URL. This information is used to " +
                        "identify you in multiplayer games and display your name to other players.\n\n" +
                        "If you choose to play as a guest, we create an anonymous account ID. " +
                        "Guest accounts do not collect any personal information.\n\n" +
                        "During online gameplay, we store temporary game room data including " +
                        "room codes, player lists, and game state. This data is automatically " +
                        "deleted when games end."
            )

            Spacer(modifier = Modifier.height(16.dp))

            PrivacySection(
                title = "How We Use Your Data",
                content = "Your account information is used solely to:\n" +
                        "• Display your name in game lobbies and during matches\n" +
                        "• Allow you to create and join online game rooms\n" +
                        "• Enable reconnection to active games if you get disconnected\n\n" +
                        "We do not use your data for advertising, analytics, or any other purpose."
            )

            Spacer(modifier = Modifier.height(16.dp))

            PrivacySection(
                title = "Data Storage",
                content = "Your account data is stored securely using Google Firebase services. " +
                        "Game room data is temporary and stored only while games are active. " +
                        "We do not sell, share, or transfer your personal data to third parties."
            )

            Spacer(modifier = Modifier.height(16.dp))

            PrivacySection(
                title = "No Advertising",
                content = "Color Clash Cards does not contain advertisements. " +
                        "We do not collect data for advertising purposes and do not share " +
                        "your information with ad networks."
            )

            Spacer(modifier = Modifier.height(16.dp))

            PrivacySection(
                title = "Data Deletion",
                content = "You can request deletion of your account data at any time by " +
                        "contacting us. Guest accounts are automatically deleted when you " +
                        "sign out. To request data deletion, please email:\n\n" +
                        "support@colorclashcards.com"
            )

            Spacer(modifier = Modifier.height(16.dp))

            PrivacySection(
                title = "Children's Privacy",
                content = "Color Clash Cards is suitable for all ages. We do not knowingly " +
                        "collect personal information from children under 13. The game can " +
                        "be played offline without any account or data collection."
            )

            Spacer(modifier = Modifier.height(16.dp))

            PrivacySection(
                title = "Contact Us",
                content = "If you have questions about this privacy policy or your data, " +
                        "please contact us at:\n\n" +
                        "support@colorclashcards.com"
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun PrivacySection(
    title: String,
    content: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = CardBlue
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
