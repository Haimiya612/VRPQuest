package com.lex.vrpquest.Managers

import com.lex.vrpquest.Utils.ShizukuShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class ShizukuInstallResult(
    val success: Boolean,
    val apkInstalled: Boolean,
    val obbInstalled: Boolean,
    val output: String
)

class ShizukuInstaller {

    companion object {
        private const val TEMP_DIRECTORY = "/data/local/tmp"
        private const val OBB_DIRECTORY = "/sdcard/Android/obb"
    }

    suspend fun installGame(
        packageName: String,
        apkFiles: List<File>,
        obbDirectory: File? = null
    ): ShizukuInstallResult = withContext(Dispatchers.IO) {

        validatePackageName(packageName)

        if (!ShizukuShell.isAvailable()) {
            throw IllegalStateException(
                "Start Shizuku and grant Rookie Quest permission first."
            )
        }

        require(apkFiles.isNotEmpty()) {
            "No APK files were provided."
        }

        apkFiles.forEach { apk ->
            require(apk.exists() && apk.isFile && apk.canRead()) {
                "APK is missing or unreadable: ${apk.name}"
            }

            require(apk.extension.equals("apk", ignoreCase = true)) {
                "Invalid APK file: ${apk.name}"
            }
        }

        if (obbDirectory != null) {
            require(obbDirectory.exists() && obbDirectory.isDirectory) {
                "The OBB directory does not exist."
            }
        }

        val sessionId = UUID.randomUUID()
            .toString()
            .replace("-", "")

        val temporaryApks = apkFiles.mapIndexed { index, _ ->
            "$TEMP_DIRECTORY/vrp_${sessionId}_$index.apk"
        }

        val commands = mutableListOf<String>()

        commands += "mkdir -p $TEMP_DIRECTORY"

        apkFiles.forEachIndexed { index, apk ->
            commands +=
                "cp -f -- ${shellQuote(apk.canonicalPath)} " +
                "${shellQuote(temporaryApks[index])} || exit 21"
        }

        val installCommand = if (temporaryApks.size == 1) {
            "pm install -r ${shellQuote(temporaryApks.first())}"
        } else {
            "pm install-multiple -r " +
                temporaryApks.joinToString(" ") {
                    shellQuote(it)
                }
        }

        commands += "$installCommand || exit 22"

        if (obbDirectory != null) {
            val destination = "$OBB_DIRECTORY/$packageName"

            commands +=
                "mkdir -p ${shellQuote(destination)} || exit 31"

            commands +=
                "cp -R ${shellQuote(obbDirectory.canonicalPath + "/.")} " +
                "${shellQuote("$destination/")} || exit 32"
        }

        val cleanupPaths = temporaryApks.joinToString(" ")

        val script = buildString {
            appendLine(
                "trap 'rm -f $cleanupPaths' EXIT"
            )

            commands.forEach { command ->
                appendLine(command)
            }

            appendLine("echo VRP_INSTALL_COMPLETE")
        }

        val result = ShizukuShell.execute(script)

        val apkInstalled =
            result.exitCode == 0 &&
            result.output.contains("Success", ignoreCase = true)

        val obbInstalled =
            apkInstalled && obbDirectory != null

        ShizukuInstallResult(
            success = apkInstalled && result.success,
            apkInstalled = apkInstalled,
            obbInstalled = obbInstalled,
            output = result.output
        )
    }

    private fun validatePackageName(packageName: String) {
        require(
            packageName.matches(
                Regex("""[A-Za-z0-9_]+(\.[A-Za-z0-9_]+)+""")
            )
        ) {
            "Invalid Android package name."
        }
    }

    private fun shellQuote(value: String): String {
        return "'" + value.replace("'", "'\\''") + "'"
    }
}