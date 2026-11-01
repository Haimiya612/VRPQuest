package com.lex.vrpquest.Managers

import android.content.Context
import com.lex.vrpquest.Utils.MirrorConfig
import com.lex.vrpquest.Utils.SevenZipExtract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class PublicMirrorException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

object PublicMirrorRepository {

    private const val USER_AGENT = "rclone/v69"
    private const val MAX_METADATA_BYTES = 512L * 1024L * 1024L

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.MINUTES)
        .followRedirects(true)
        .followSslRedirects(false)
        .build()

    suspend fun fetchGames(
        context: Context,
        config: MirrorConfig,
        progress: (Float) -> Unit = {}
    ): List<Game> = withContext(Dispatchers.IO) {

        val root = File(context.filesDir, "public-mirror")
        val archive = File(root, "meta.7z")
        val partial = File(root, "meta.7z.part")
        val extracted = File(root, "meta-staging")
        val metadata = File(root, "meta")

        if (!root.exists() && !root.mkdirs()) {
            throw PublicMirrorException("Unable to create mirror storage.")
        }

        partial.delete()
        extracted.deleteRecursively()

        val archiveUrl = config.baseUri.trimEnd('/') + "/meta.7z"

        progress(0.02f)

        val request = Request.Builder()
            .url(archiveUrl)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "*/*")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->

                if (!response.isSuccessful) {
                    throw PublicMirrorException(
                        "Mirror returned HTTP ${response.code}."
                    )
                }

                val body = response.body
                    ?: throw PublicMirrorException(
                        "Mirror returned an empty response."
                    )

                val length = body.contentLength()

                if (length > MAX_METADATA_BYTES) {
                    throw PublicMirrorException(
                        "Metadata archive exceeds the size limit."
                    )
                }

                body.byteStream().use { input ->
                    FileOutputStream(partial).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var total = 0L

                        while (true) {
                            val count = input.read(buffer)
                            if (count == -1) break

                            total += count

                            if (total > MAX_METADATA_BYTES) {
                                throw PublicMirrorException(
                                    "Metadata archive exceeds the size limit."
                                )
                            }

                            output.write(buffer, 0, count)

                            if (length > 0L) {
                                progress(
                                    0.02f + 0.38f *
                                        (total.toFloat() / length).coerceIn(0f, 1f)
                                )
                            }
                        }

                        output.fd.sync()
                    }
                }
            }

            if (partial.length() <= 0L) {
                throw PublicMirrorException(
                    "The metadata archive is empty."
                )
            }

            if (!partial.renameTo(archive)) {
                throw PublicMirrorException(
                    "Unable to finalize metadata download."
                )
            }

            progress(0.42f)

            SevenZipExtract(
                archive = archive.absolutePath,
                outputDirectory = extracted.absolutePath,
                IsSingularZip = true,
                pass = config.password,
                progress = {
                    progress(0.42f + it.coerceIn(0f, 1f) * 0.43f)
                }
            )

            val gameList = File(extracted, "VRP-GameList.txt")

            if (!gameList.isFile) {
                throw PublicMirrorException(
                    "VRP-GameList.txt was not found in the metadata archive."
                )
            }

            val games = parseGameList(extracted, gameList)

            if (games.isEmpty()) {
                throw PublicMirrorException(
                    "The metadata archive was extracted, but no valid games were found."
                )
            }

            metadata.deleteRecursively()

            if (!extracted.renameTo(metadata)) {
                throw PublicMirrorException(
                    "Unable to move extracted metadata into place."
                )
            }

            progress(1f)
            games

        } catch (e: PublicMirrorException) {
            partial.delete()
            throw e
        } catch (e: Exception) {
            partial.delete()
            throw PublicMirrorException(
                e.message ?: "Unable to load public mirror metadata.",
                e
            )
        }
    }

    private fun parseGameList(
        metadataDirectory: File,
        gameList: File
    ): List<Game> {

        val lines = gameList.readLines(Charsets.UTF_8)
            .drop(1)

        val result = ArrayList<Game>(lines.size)

        for (line in lines) {
            if (line.isBlank()) continue

            val fields = line.removePrefix("\uFEFF").split(";")
            if (fields.size < 6) continue

            val gameName = fields[0].trim()
            val releaseName = fields[1].trim()
            val packageName = fields[2].trim()
            val versionCode = fields[3].trim()
            val lastUpdated = fields[4].trim()
            val size = fields[5].trim().toIntOrNull() ?: 0

            if (gameName.isBlank() || releaseName.isBlank()) continue

            if (!packageName.matches(
                    Regex("""[A-Za-z0-9_]+(\.[A-Za-z0-9_]+)+""")
                )
            ) continue

            val thumbnail = File(
                metadataDirectory,
                ".meta/thumbnails/$packageName.jpg"
            )

            val notesFile = File(
                metadataDirectory,
                ".meta/notes/$releaseName.txt"
            )

            result.add(
                Game(
                    GameName = gameName,
                    ReleaseName = releaseName,
                    PackageName = packageName,
                    VersionCode = versionCode,
                    LastUpdated = lastUpdated,
                    Thumbnail = thumbnail.takeIf { it.isFile }?.absolutePath ?: "",
                    Size = size,
                    IsInstalled = false,
                    notes = notesFile.takeIf { it.isFile }
                        ?.readText(Charsets.UTF_8)
                        .orEmpty()
                )
            )
        }

        return result
    }
}