package com.lex.vrpquest.Managers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class CatalogApp(
    val id: String,
    val title: String,
    val packageName: String,
    val versionName: String,
    val description: String,
    val iconUrl: String?,
    val downloadUrl: String,
    val sha256: String?
)

class CatalogException(message: String) :
    Exception(message)

class CatalogRepository(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()
) {

    suspend fun fetchCatalog(url: String): List<CatalogApp> =
        withContext(Dispatchers.IO) {

            validateHttpsUrl(url)

            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .get()
                .build()

            client.newCall(request).execute().use { response ->

                if (!response.isSuccessful) {
                    throw CatalogException(
                        "Catalog returned HTTP ${response.code}"
                    )
                }

                val body = response.body?.string()
                    ?: throw CatalogException("Empty response")

                if (body.length > 5_000_000) {
                    throw CatalogException("Catalog is too large")
                }

                parseCatalog(body)
            }
        }

    private fun parseCatalog(json: String): List<CatalogApp> {

        val root = try {
            JSONObject(json)
        } catch (e: Exception) {
            throw CatalogException("Invalid JSON catalog")
        }

        val apps = root.optJSONArray("apps")
            ?: throw CatalogException("Missing apps array")

        if (apps.length() > 2000) {
            throw CatalogException("Too many catalog entries")
        }

        return buildList {

            for (i in 0 until apps.length()) {

                val item = apps.optJSONObject(i)
                    ?: continue

                val id = item.optString("id")
                val title = item.optString("title")
                val packageName = item.optString("packageName")
                val versionName = item.optString("versionName")
                val description = item.optString("description")
                val downloadUrl = item.optString("downloadUrl")

                if (id.isBlank() || title.isBlank()) continue

                if (!packageName.matches(
                    Regex("""[A-Za-z0-9_]+(\.[A-Za-z0-9_]+)+""")
                )) continue

                if (versionName.isBlank()) continue

                try {
                    validateHttpsUrl(downloadUrl)

                    val iconUrl = item
                        .optString("iconUrl")
                        .takeIf { it.isNotBlank() }
                        ?.also { validateHttpsUrl(it) }

                    val hash = item
                        .optString("sha256")
                        .takeIf { it.isNotBlank() }

                    if (hash != null &&
                        !hash.matches(Regex("[a-fA-F0-9]{64}"))
                    ) continue

                    add(
                        CatalogApp(
                            id = id,
                            title = title,
                            packageName = packageName,
                            versionName = versionName,
                            description = description,
                            iconUrl = iconUrl,
                            downloadUrl = downloadUrl,
                            sha256 = hash
                        )
                    )

                } catch (_: Exception) {
                    continue
                }
            }
        }
    }

    private fun validateHttpsUrl(value: String) {
        val parsed = java.net.URI(value)

        if (parsed.scheme != "https" ||
            parsed.host.isNullOrBlank() ||
            parsed.userInfo != null
        ) {
            throw CatalogException("Only HTTPS URLs are accepted")
        }
    }
}