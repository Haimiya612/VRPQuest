@Composable
fun MirrorSettings() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var configUrl by remember { mutableStateOf("") }
    var jsonInput by remember { mutableStateOf("") }

    var savedConfig by remember {
        mutableStateOf(
            runCatching {
                MirrorConfigStore.load(context)
            }.getOrNull()
        )
    }

    var status by remember {
        mutableStateOf(
            if (savedConfig != null) {
                "A mirror configuration is saved."
            } else {
                "No mirror configured."
            }
        )
    }

    var busy by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Load configuration from URL",
            color = Accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )

        OutlinedTextField(
            value = configUrl,
            onValueChange = { configUrl = it },
            label = { Text("Configuration URL") },
            placeholder = { Text("https://example.com/config.json") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            enabled = !busy && configUrl.isNotBlank(),
            onClick = {
                scope.launch {
                    busy = true
                    status = "Downloading configuration..."

                    try {
                        val config = withContext(Dispatchers.IO) {
                            MirrorConfigStore.fetchFromUrl(configUrl)
                        }

                        withContext(Dispatchers.IO) {
                            MirrorConfigStore.save(context, config)
                        }

                        savedConfig = config
                        status = "Configuration downloaded and saved."
                    } catch (e: Exception) {
                        status = "Failed: ${e.message}"
                    } finally {
                        busy = false
                    }
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = Accent
            )
        ) {
            Text("Load URL and save", color = Background)
        }

        HorizontalDivider(color = PanelLight)

        Text(
            "Or paste configuration JSON",
            color = Accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )

        OutlinedTextField(
            value = jsonInput,
            onValueChange = { jsonInput = it },
            label = { Text("Mirror JSON") },
            placeholder = {
                Text(
                    """
                    {
                      "baseUri": "https://example.com/",
                      "password": "BASE64_PASSWORD"
                    }
                    """.trimIndent()
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(145.dp)
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            OutlinedButton(
                enabled = !busy && jsonInput.isNotBlank(),
                onClick = {
                    try {
                        MirrorConfigStore.parseJson(jsonInput)
                        status = "JSON is valid. Ready to save."
                    } catch (e: Exception) {
                        status = "Invalid JSON: ${e.message}"
                    }
                }
            ) {
                Text("Validate")
            }

            Button(
                enabled = !busy && jsonInput.isNotBlank(),
                onClick = {
                    scope.launch {
                        busy = true

                        try {
                            val config = MirrorConfigStore.parseJson(jsonInput)

                            withContext(Dispatchers.IO) {
                                MirrorConfigStore.save(context, config)
                            }

                            savedConfig = config
                            status = "Configuration saved securely."
                        } catch (e: Exception) {
                            status = "Save failed: ${e.message}"
                        } finally {
                            busy = false
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Accent
                )
            ) {
                Text("Save configuration", color = Background)
            }
        }

        Text(
            status,
            color = TextSecondary,
            fontSize = 12.sp
        )

        if (savedConfig != null) {
            Surface(
                color = PanelLight,
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Text(
                        "Saved mirror",
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )

                    Text(
                        savedConfig!!.baseUri,
                        color = TextSecondary,
                        fontSize = 11.sp
                    )

                    Text(
                        "Password: encrypted and stored",
                        color = Success,
                        fontSize = 11.sp
                    )

                    OutlinedButton(
                        enabled = !busy,
                        onClick = {
                            scope.launch {
                                busy = true

                                try {
                                    withContext(Dispatchers.IO) {
                                        MirrorConfigStore.clear(context)
                                    }

                                    savedConfig = null
                                    jsonInput = ""
                                    configUrl = ""
                                    status = "Mirror disconnected."
                                } catch (e: Exception) {
                                    status = "Disconnect failed: ${e.message}"
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    ) {
                        Text("Disconnect")
                    }
                }
            }
        }

        if (busy) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = Accent
            )
        }
    }
}