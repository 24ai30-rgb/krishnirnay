package com.krishinirnay.core.designsystem.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * The one labeled text field every form screen should use — shows a
 * required (*) or "(optional)" hint next to the label so a farmer always
 * knows whether a blank field will block Continue. [onValueChange] is last
 * so callers can use trailing-lambda syntax. See [OnboardingScreen] for the
 * primary consumer.
 */
@Composable
fun KrishiTextField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    required: Boolean = false,
    optionalLabel: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    onValueChange: (String) -> Unit,
) {
    val displayLabel = when {
        required -> "$label *"
        optionalLabel != null -> "$label ($optionalLabel)"
        else -> label
    }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(displayLabel) },
        singleLine = singleLine,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        ),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier.fillMaxWidth(),
    )
}
