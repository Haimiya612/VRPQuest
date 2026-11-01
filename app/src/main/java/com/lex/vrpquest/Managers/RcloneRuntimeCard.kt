package com.lex.vrpquest

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lex.vrpquest.Managers.RcloneRuntime
import kotlinx.coroutines.launch

@Composable
fun RcloneRuntimeCard() {

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var running by remember { mutableStateOf(false) }
    var output by remember {
        mutableStateOf("Rclone has not been tested yet.")
    }

    var successful by remember {
        mutableStateOf<Boolean?>(null)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Rclone runtime",
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                "Tests whether the native ARM64 Rclone executable can run directly on this device.",
                style = MaterialTheme.typography.bodySmall
            )

            Button(
                onClick = {
                    scope.launch {
                        running = true
                        successful = null
                        output = "Testing native executable..."

                        try {
                            val result = RcloneRuntime.verify(context)

                            successful = result.success
                            output = buildString {
                                appendLine("Exit code: ${result.exitCode}")
                                appendLine()
                                append(result.output)
                            }

                        } catch (e: Exception) {
                            successful = false
                            output = e.message ?: "Unknown runtime error."
                        } finally {
                            running = false
                        }
                    }
                },
                enabled = !running
            ) {
                Text(
                    if (running) "Testing..."
                    else "Test Rclone"
                )
            }

            successful?.let { passed ->
                Text(
                    if (passed) "Rclone executable started successfully."
                    else "Rclone runtime test failed.",
                    color = if (passed) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    output,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}