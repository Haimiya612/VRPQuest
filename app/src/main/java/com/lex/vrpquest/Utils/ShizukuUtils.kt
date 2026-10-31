
package com.lex.vrpquest.Utils

import android.content.pm.PackageManager
import android.os.ParcelFileDescriptor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

data class ShizukuCommandResult(
    val exitCode: Int,
    val output: String
) {
    val success: Boolean
        get() = exitCode == 0
}

object ShizukuShell {

    fun isAvailable(): Boolean {
        return try {
            Shizuku.pingBinder() &&
                Shizuku.checkSelfPermission() ==
                PackageManager.PERMISSION_GRANTED
        } catch (_: Exception) {
            false
        }
    }

    fun requestPermission(requestCode: Int): Boolean {
        return try {
            if (!Shizuku.pingBinder()) return false
            if (Shizuku.isPreV11()) return false

            if (Shizuku.checkSelfPermission() ==
                PackageManager.PERMISSION_GRANTED
            ) {
                true
            } else {
                Shizuku.requestPermission(requestCode)
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    suspend fun execute(
        command: String
    ): ShizukuCommandResult = withContext(Dispatchers.IO) {
        executeBlocking(command)
    }

    fun executeBlocking(
        command: String
    ): ShizukuCommandResult {

        check(isAvailable()) {
            "Shizuku is unavailable or permission was not granted."
        }

        require(command.isNotBlank()) {
            "Command cannot be empty."
        }

        val service = IShizukuService.Stub.asInterface(
            Shizuku.getBinder()
        ) ?: throw IllegalStateException(
            "Could not connect to Shizuku service."
        )

        val script = """
            exec 2>&1
            set +e
            $command
            __vrp_exit_code=$?
            printf '\n__VRP_EXIT_CODE__:%s\n' "${'$'}__vrp_exit_code"
            exit "${'$'}__vrp_exit_code"
        """.trimIndent()

        val process = service.newProcess(
            arrayOf("sh", "-c", script),
            null,
            null
        )

        val output = ParcelFileDescriptor.AutoCloseInputStream(
            process.inputStream
        ).use { stream ->
            BufferedReader(InputStreamReader(stream)).readText()
        }

        val processExitCode = process.waitFor()

        val marker = Regex(
            """__VRP_EXIT_CODE__:(-?\d+)"""
        ).find(output)

        val exitCode = marker
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?: processExitCode

        val cleanOutput = output
            .replace(
                Regex("""\n?__VRP_EXIT_CODE__:-?\d+\s*$"""),
                ""
            )
            .trim()

        return ShizukuCommandResult(
            exitCode = exitCode,
            output = cleanOutput
        )
    }
}

/**
 * Backwards-compatible wrapper for existing DownloadManager code.
 * New installation code should use ShizukuShell.execute().
 */
fun ShizAdbCommand(command: String): String {
    return try {
        val result = ShizukuShell.executeBlocking(command)

        if (result.success) {
            result.output
        } else {
            "SHIZUKU_COMMAND_FAILED (${result.exitCode}): ${result.output}"
        }
    } catch (e: Exception) {
        "SHIZUKU_ERROR: ${e.message}"
    }
}

fun canUseShizuku(): Boolean {
    return ShizukuShell.isAvailable()
}

fun IsShizukuGranted(): Boolean {
    return try {
        Shizuku.checkSelfPermission() ==
            PackageManager.PERMISSION_GRANTED
    } catch (_: Exception) {
        false
    }
}

fun checkPermission(code: Int): Boolean {
    return ShizukuShell.requestPermission(code)
}

@Composable
fun UpdateShizukuState(Callback: () -> Unit) {
    DisposableEffect(Unit) {

        val binderReceived =
            Shizuku.OnBinderReceivedListener {
                Callback()
            }

        val binderDead =
            Shizuku.OnBinderDeadListener {
                Callback()
            }

        val permissionResult =
            Shizuku.OnRequestPermissionResultListener {
                    requestCode,
                    grantResult ->
                Callback()
            }

        Shizuku.addBinderReceivedListener(binderReceived)
        Shizuku.addBinderDeadListener(binderDead)
        Shizuku.addRequestPermissionResultListener(permissionResult)

        onDispose {
            Shizuku.removeBinderReceivedListener(binderReceived)
            Shizuku.removeBinderDeadListener(binderDead)
            Shizuku.removeRequestPermissionResultListener(permissionResult)
        }
    }
}

fun onRequestPermissionsResult(
    requestCode: Int,
    grantResult: Int
) {
    // Read the latest permission state through ShizukuShell.isAvailable().
}

val REQUEST_PERMISSION_RESULT_LISTENER =
    Shizuku.OnRequestPermissionResultListener {
            requestCode,
            grantResult ->

        onRequestPermissionsResult(
            requestCode,
            grantResult
        )
    }