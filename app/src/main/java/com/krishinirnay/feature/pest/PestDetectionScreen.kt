package com.krishinirnay.feature.pest

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.File

@Composable
fun PestDetectionScreen(
    viewModel: PestDetectionViewModel = hiltViewModel(),
) {
    val context = LocalContext.current

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var selectedUri by remember {
        mutableStateOf<Uri?>(null)
    }

    val imagePicker =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent(),
        ) { uri ->
            selectedUri = uri
        }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.Center,
    ) {

        Text(
            text = "🐛 Pest Detection",
        )

        Spacer(
            modifier = Modifier.height(20.dp),
        )

        Button(
            onClick = {
                imagePicker.launch("image/*")
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Select Plant Image")
        }

        Spacer(
            modifier = Modifier.height(16.dp),
        )

        if (selectedUri != null) {

            Button(
                onClick = {

                    val uri = selectedUri ?: return@Button

                    val file = File(
                        context.cacheDir,
                        "pest_image.jpg",
                    )

                    context.contentResolver
                        .openInputStream(uri)
                        ?.use { input ->
                            file.outputStream()
                                .use { output ->
                                    input.copyTo(output)
                                }
                        }

                    viewModel.detectPest(file)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Detect Pest")
            }
        }

        Spacer(
            modifier = Modifier.height(20.dp),
        )

        when {
            uiState.isLoading -> {

                CircularProgressIndicator()

                Spacer(
                    modifier = Modifier.height(12.dp),
                )

                Text(
                    text = "Analyzing image...",
                )
            }

            uiState.result != null -> {

                uiState.result?.let { result ->

                    if (
                        result.detected &&
                        result.top_detection != null
                    ) {

                        Text(
                            text =
                                "Pest: ${
                                    result.top_detection.class_name
                                }",
                        )

                        Spacer(
                            modifier = Modifier.height(8.dp),
                        )

                        Text(
                            text =
                                "Confidence: ${
                                    String.format(
                                        "%.1f",
                                        result.top_detection.confidence * 100f,
                                    )
                                }%",
                        )

                        Spacer(
                            modifier = Modifier.height(8.dp),
                        )

                        Text(
                            text =
                                "Detected: ${result.count}",
                        )

                    } else {

                        Text(
                            text = "No pest detected.",
                        )
                    }
                }
            }

            uiState.error != null -> {

                Text(
                    text = "Error: ${uiState.error}",
                )
            }
        }
    }
}