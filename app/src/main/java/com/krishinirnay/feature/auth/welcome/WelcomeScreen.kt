package com.krishinirnay.feature.auth.welcome

import com.krishinirnay.core.designsystem.theme.KrishiTheme
import com.krishinirnay.core.designsystem.motion.pulse
import com.krishinirnay.core.designsystem.motion.enterStagger
import com.krishinirnay.core.designsystem.components.KnButtonStyle
import com.krishinirnay.core.designsystem.components.KnButton
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.navigationBarsPadding
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
    val c = KrishiTheme.colors
    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Brand hero: full-bleed gradient with the sprout mark and name.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp))
                .background(Brush.linearGradient(listOf(c.heroStart, c.heroEnd))),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 28.dp)) {
                Box(
                    modifier = Modifier
                        .enterStagger(0)
                        .pulse(true)
                        .size(104.dp)
                        .clip(RoundedCornerShape(32.dp))
                        .background(Color.White.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    SproutMark(modifier = Modifier.size(54.dp), color = c.lime)
                }
                Spacer(Modifier.height(22.dp))
                Text(
                    "KrishiNirnay",
                    style = MaterialTheme.typography.displayMedium,
                    color = Color.White,
                    modifier = Modifier.enterStagger(1),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = strings.loginTagline,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.88f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.enterStagger(2),
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            KnButton(
                text = strings.loginCreateAccount,
                onClick = onCreateAccount,
                modifier = Modifier.fillMaxWidth().enterStagger(3),
            )
            KnButton(
                text = strings.loginSubmit,
                onClick = onLogIn,
                style = KnButtonStyle.Secondary,
                modifier = Modifier.fillMaxWidth().enterStagger(4),
            )
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
