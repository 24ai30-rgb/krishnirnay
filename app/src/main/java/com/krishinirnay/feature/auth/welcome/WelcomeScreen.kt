package com.krishinirnay.feature.auth.welcome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.krishinirnay.core.designsystem.components.SproutMark
import com.krishinirnay.core.designsystem.strings.LocalAppStrings
import com.krishinirnay.core.designsystem.theme.KrishiNirnayTheme

/**
 * The true cold-start landing screen (Phase 5 Part 1): Welcome -> Register ->
 * Login -> Farmer Profile Setup -> Home. AuthGateViewModel starts the app
 * here whenever there is no persisted Firebase session — Login/Register are
 * both one tap away, but neither is assumed; a farmer explicitly picks
 * "Create Account" (new) or "Log In" (returning) rather than always landing
 * on a login form as if every farmer already had an account.
 */
@Composable
fun WelcomeScreen(
    onCreateAccount: () -> Unit,
    onLogIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(modifier = Modifier.size(120.dp), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .background(
                            Brush.radialGradient(
                                listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f), Color.Transparent),
                            ),
                        ),
                )
                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(24.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    SproutMark(modifier = Modifier.size(42.dp), color = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.height(20.dp))
            Text("KrishiNirnay", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                text = strings.loginTagline,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(56.dp))

            Button(
                onClick = onCreateAccount,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Text(strings.loginCreateAccount, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onLogIn,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Text(strings.loginSubmit, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
private fun WelcomeScreenPreview() {
    KrishiNirnayTheme {
        WelcomeScreen(onCreateAccount = {}, onLogIn = {})
    }
}
