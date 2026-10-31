package com.lex.vrpquest.Utils

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URI
import java.security.KeyStore
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class MirrorConfig(
    val baseUri: String,
    val password: String,
    val sourceUrl: String? = null
)

object MirrorConfigStore {

    private const val KEY_ALIAS = "vrp_mirror_config_key"
    private const val PREFS_NAME = "secure_mirror_config"
    private const val CONFIG_KEY = "encrypted_config"
    private const val IV_KEY = "config_iv"

    private const val AAD =
        "com.lex.vrpquest.mirror-config.v1"

    private const val MAX_CONFIG_SIZE = 64 * 1024

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .build()

    // --------------------------------------------------
    // CONFIGURATION PARSING
    // --------------------------------------------------

    fun parseJson(
        json: String,
        sourceUrl: String? = null
    ): MirrorConfig {

        require(json.isNotBlank()) {
            "Configuration JSON is empty."
        }

        require(json.toByteArray(Charsets.UTF_8).size <= MAX_CONFIG_SIZE) {
            "Configuration JSON is too large."
        }

        val objectJson = JSONObject(json)

        val baseUri = normalizeBaseUri(
            objectJson.getString("baseUri")
        )

        val encodedPassword =
            objectJson.getString("password").trim()

        require(encodedPassword.isNotBlank()) {
            "Mirror password is missing."
        }

        val decodedPassword = try {
            String(
                Base64.decode(
                    encodedPassword,
                    Base64.DEFAULT
                ),
                Charsets.UTF_8
            )
        } catch (_: Exception) {
            throw IllegalArgumentException(
                "Mirror password is not valid Base64."
            )
        }

        require(decodedPassword.isNotBlank()) {
            "Decoded mirror password is empty."
        }

        return MirrorConfig(
            baseUri = baseUri,
            password = decodedPassword,
            sourceUrl = sourceUrl?.let {
                validateHttpsUrl(it)
            }
        )
    }

    private fun normalizeBaseUri(value: String): String {
        val uri = try {
            URI(value.trim())
        } catch (_: Exception) {
            throw IllegalArgumentException(
                "Invalid mirror address."
            )
        }

        require(uri.scheme.equals("https", true)) {
            "Mirror address must use HTTPS."
        }

        require(!uri.host.isNullOrBlank()) {
            "Mirror address has no hostname."
        }

        require(uri.userInfo == null) {
            "Credentials must not be embedded in the mirror URL."
        }

        require(uri.rawQuery == null && uri.rawFragment == null) {
            "Mirror address cannot contain a query or fragment."
        }

        val normalized = uri.toASCIIString()

        return if (normalized.endsWith("/")) {
            normalized
        } else {
            "$normalized/"
        }
    }

    private fun validateHttpsUrl(value: String): String {
        val uri = URI(value.trim())

        require(uri.scheme.equals("https", true)) {
            "Configuration URL must use HTTPS."
        }

        require(!uri.host.isNullOrBlank() && uri.userInfo == null) {
            "Invalid configuration URL."
        }

        return uri.toASCIIString()
    }

    // --------------------------------------------------
    // FETCH CONFIGURATION FROM URL
    // --------------------------------------------------

    suspend fun fetchFromUrl(url: String): MirrorConfig =
        withContext(Dispatchers.IO) {

            val safeUrl = validateHttpsUrl(url)

            val request = Request.Builder()
                .url(safeUrl)
                .header("Accept", "application/json")
                .header("User-Agent", "VRPQuest")
                .get()
                .build()

            client.newCall(request).execute().use { response ->

                require(response.isSuccessful) {
                    "Configuration request failed: HTTP ${response.code}"
                }

                val body = response.body
                    ?: throw IllegalStateException(
                        "Configuration response was empty."
                    )

                require(
                    body.contentLength() == -1L ||
                    body.contentLength() <= MAX_CONFIG_SIZE
                ) {
                    "Configuration response is too large."
                }

                val json = body.string()

                parseJson(
                    json = json,
                    sourceUrl = safeUrl
                )
            }
        }

    // --------------------------------------------------
    // SECURE STORAGE
    // --------------------------------------------------

    fun save(
        context: Context,
        config: MirrorConfig
    ) {

        val json = JSONObject().apply {
            put("baseUri", config.baseUri)
            put("password", config.password)
            put("sourceUrl", config.sourceUrl ?: JSONObject.NULL)
        }.toString()

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")

        cipher.init(
            Cipher.ENCRYPT_MODE,
            getOrCreateKey()
        )

        cipher.updateAAD(AAD.toByteArray(Charsets.UTF_8))

        val encrypted = cipher.doFinal(
            json.toByteArray(Charsets.UTF_8)
        )

        val prefs = context.applicationContext
            .getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )

        val saved = prefs.edit()
            .putString(
                CONFIG_KEY,
                Base64.encodeToString(
                    encrypted,
                    Base64.NO_WRAP
                )
            )
            .putString(
                IV_KEY,
                Base64.encodeToString(
                    cipher.iv,
                    Base64.NO_WRAP
                )
            )
            .commit()

        check(saved) {
            "Unable to save encrypted mirror configuration."
        }
    }

    fun load(context: Context): MirrorConfig? {

        val prefs = context.applicationContext
            .getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )

        val encryptedText = prefs.getString(CONFIG_KEY, null)
            ?: return null

        val ivText = prefs.getString(IV_KEY, null)
            ?: throw IllegalStateException(
                "Mirror configuration is incomplete."
            )

        val encrypted = Base64.decode(
            encryptedText,
            Base64.NO_WRAP
        )

        val iv = Base64.decode(
            ivText,
            Base64.NO_WRAP
        )

        require(iv.size == 12) {
            "Invalid mirror configuration encryption data."
        }

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")

        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(128, iv)
        )

        cipher.updateAAD(AAD.toByteArray(Charsets.UTF_8))

        val decrypted = cipher.doFinal(encrypted)

        val json = JSONObject(
            String(decrypted, Charsets.UTF_8)
        )

        return MirrorConfig(
            baseUri = normalizeBaseUri(
                json.getString("baseUri")
            ),
            password = json.getString("password"),
            sourceUrl = json.optString("sourceUrl")
                .takeIf { it.isNotBlank() && it != "null" }
        )
    }

    fun clear(context: Context) {

        context.applicationContext
            .getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )
            .edit()
            .clear()
            .commit()

        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        keyStore.deleteEntry(KEY_ALIAS)
    }

    fun isConfigured(context: Context): Boolean {
        return load(context) != null
    }

    // --------------------------------------------------
    // ANDROID KEYSTORE
    // --------------------------------------------------

    private fun getOrCreateKey(): SecretKey {

        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)

        val existing = keyStore.getKey(
            KEY_ALIAS,
            null
        ) as? SecretKey

        if (existing != null) {
            return existing
        }

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )

        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or
                KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(
                KeyProperties.ENCRYPTION_PADDING_NONE
            )
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()

        generator.init(spec)

        return generator.generateKey()
    }
}