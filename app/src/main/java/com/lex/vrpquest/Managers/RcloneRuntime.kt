package com.lex.vrpquest.Managers

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

data class RcloneResult(
    val exitCode: Int,
    val output: String
) {
    val success: Boolean
        get() = exitCode == 0
}

object RcloneRuntime {

    const val EXPECTED_VERSION = "v1.75.0"

    private const val MAX_OUTPUT_SIZE = 1024 * 1024
    private const val DEFAULT_TIMEOUT_SECONDS = 30L

    private fun executable(context: Context): File {
        return File(
            context.applicationInfo.nativeLibraryDir,
            "librclone.so"
        )
    }

    suspend fun verify(
        context: Context
    ): RcloneResult = execute(
        context = context,
        arguments = listOf("version"),
        timeoutSeconds = DEFAULT_TIMEOUT_SECONDS
    )

    suspend fun execute(
        context: Context,
        arguments: List<String>,
        workingDirectory: File? = null,
        timeoutSeconds: Long = DEFAULT_TIMEOUT_SECONDS
    ): RcloneResult = withContext(Dispatchers.IO) {

        require(arguments.isNotEmpty()) {
            "No Rclone arguments supplied."
        }

        require(timeoutSeconds > 0) {
            "Timeout must be greater than zero."
        }

        val appContext = context.applicationContext
        val binary = executable(appContext)

        check(binary.isFile) {
            "Rclone executable is missing. Rebuild the APK with the Rclone runtime included."
        }

        val configDirectory = File(
            appContext.noBackupFilesDir,
            "rclone"
        )

        check(configDirectory.mkdirs() || configDirectory.isDirectory) {
            "Unable to create Rclone configuration directory."
        }

        val configFile = File(
            configDirectory,
            "rclone.conf"
        )

        if (!configFile.exists()) {
            check(configFile.createNewFile()) {
                "Unable to create Rclone configuration file."
            }
        }

        val cacheDirectory = File(
            appContext.cacheDir,
            "rclone"
        )

        check(cacheDirectory.mkdirs() || cacheDirectory.isDirectory) {
            "Unable to create Rclone cache directory."
        }

        val outputFile = File(
            cacheDirectory,
            "run-${UUID.randomUUID()}.log"
        )

        try {
            val command = ArrayList<String>(arguments.size + 1)
            command.add(binary.absolutePath)
            command.addAll(arguments)

            val builder = ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(outputFile)

            builder.environment()["RCLONE_CONFIG"] =
                configFile.absolutePath

            builder.environment()["RCLONE_CACHE_DIR"] =
                cacheDirectory.absolutePath

            builder.environment()["HOME"] =
                appContext.filesDir.absolutePath

            if (workingDirectory != null) {
                require(
                    workingDirectory.exists() &&
                    workingDirectory.isDirectory
                ) {
                    "Rclone working directory does not exist."
                }

                builder.directory(workingDirectory)
            } else {
                builder.directory(appContext.filesDir)
            }

            val process = try {
                builder.start()
            } catch (e: Exception) {
                throw IllegalStateException(
                    "Android could not execute Rclone: ${e.message}",
                    e
                )
            }

            val completed = process.waitFor(
                timeoutSeconds,
                TimeUnit.SECONDS
            )

            if (!completed) {
                process.destroyForcibly()
                process.waitFor()

                throw IllegalStateException(
                    "Rclone timed out after $timeoutSeconds seconds."
                )
            }

            val output = if (outputFile.exists()) {
                outputFile.inputStream().buffered().use { stream ->
                    val bytes = ByteArray(MAX_OUTPUT_SIZE)
                    val count = stream.read(bytes)

                    if (count > 0) {
                        String(bytes, 0, count, Charsets.UTF_8)
                    } else {
                        ""
                    }
                }
            } else {
                ""
            }

            RcloneResult(
                exitCode = process.exitValue(),
                output = output.trim()
            )

        } finally {
            outputFile.delete()
        }
    }
}