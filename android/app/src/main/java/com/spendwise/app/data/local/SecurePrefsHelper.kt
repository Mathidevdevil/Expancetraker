package com.spendwise.app.data.local

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface TextEncryptor {
    fun encrypt(plainText: String): String
    fun decrypt(cipherText: String): String?
}

/**
 * Android Keystore AES-256-GCM encryptor for API 26+ without deprecated alpha dependencies.
 */
class AndroidKeystoreEncryptor(
    private val keyAlias: String = "spendwise_prefs_master_key_v1"
) : TextEncryptor {

    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = ks.getKey(keyAlias, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    override fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
        val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)
        val dataB64 = Base64.encodeToString(encrypted, Base64.NO_WRAP)
        return "v1:$ivB64:$dataB64"
    }

    override fun decrypt(cipherText: String): String? {
        val parts = cipherText.split(":")
        if (parts.size != 3 || parts[0] != "v1") return null
        val iv = runCatching { Base64.decode(parts[1], Base64.NO_WRAP) }.getOrNull() ?: return null
        val data = runCatching { Base64.decode(parts[2], Base64.NO_WRAP) }.getOrNull() ?: return null
        return runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(data), StandardCharsets.UTF_8)
        }.getOrNull()
    }
}

/**
 * Encrypted preference wrapper with one-time idempotent migration from legacy plaintext
 * `spendwise_prefs` -> `spendwise_secure_prefs`. Preserves signed-in sessions and user
 * preferences seamlessly while removing plaintext auth & profile tokens.
 */
class SecurePrefsHelper(
    private val securePrefs: SharedPreferences,
    private val legacyPrefs: SharedPreferences?,
    private val encryptor: TextEncryptor
) {
    companion object {
        private const val LEGACY_PREFS_NAME = "spendwise_prefs"
        private const val SECURE_PREFS_NAME = "spendwise_secure_prefs"
        private const val MIGRATION_DONE_KEY = "_spendwise_migrated_v1"

        private val LEGACY_STRING_KEYS = listOf(
            "selected_currency",
            "selected_currency_symbol",
            "auth_user_id",
            "theme_mode",
            "voice_language",
            "user_name",
            "user_email",
            "user_initials"
        )
        private val LEGACY_BOOLEAN_KEYS = listOf("is_logged_in")

        fun create(context: Context): SecurePrefsHelper {
            val secure = context.getSharedPreferences(SECURE_PREFS_NAME, Context.MODE_PRIVATE)
            val legacy = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
            return SecurePrefsHelper(secure, legacy, AndroidKeystoreEncryptor()).apply {
                migrateFromLegacyIfNeeded()
            }
        }
    }

    @Synchronized
    fun migrateFromLegacyIfNeeded() {
        if (securePrefs.getBoolean(MIGRATION_DONE_KEY, false)) return
        val legacy = legacyPrefs
        if (legacy != null) {
            val secureEditor = securePrefs.edit()
            val legacyEditor = legacy.edit()

            for (key in LEGACY_STRING_KEYS) {
                if (legacy.contains(key)) {
                    val raw = legacy.getString(key, null)
                    if (raw != null && !securePrefs.contains(key)) {
                        secureEditor.putString(key, encryptor.encrypt(raw))
                    }
                    legacyEditor.remove(key)
                }
            }

            for (key in LEGACY_BOOLEAN_KEYS) {
                if (legacy.contains(key)) {
                    val raw = legacy.getBoolean(key, false)
                    if (!securePrefs.contains(key)) {
                        secureEditor.putString(key, encryptor.encrypt(raw.toString()))
                    }
                    legacyEditor.remove(key)
                }
            }

            secureEditor.putBoolean(MIGRATION_DONE_KEY, true).apply()
            legacyEditor.apply()
        } else {
            securePrefs.edit().putBoolean(MIGRATION_DONE_KEY, true).apply()
        }
    }

    fun getString(key: String, defaultValue: String? = null): String? {
        val stored = securePrefs.getString(key, null) ?: return defaultValue
        return encryptor.decrypt(stored) ?: defaultValue
    }

    fun putString(key: String, value: String) {
        securePrefs.edit().putString(key, encryptor.encrypt(value)).apply()
    }

    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean {
        val decrypted = getString(key, null) ?: return defaultValue
        return when (decrypted.lowercase()) {
            "true" -> true
            "false" -> false
            else -> defaultValue
        }
    }

    fun putBoolean(key: String, value: Boolean) {
        putString(key, value.toString())
    }

    fun putStrings(entries: Map<String, String>) {
        val editor = securePrefs.edit()
        entries.forEach { (k, v) -> editor.putString(k, encryptor.encrypt(v)) }
        editor.apply()
    }
}
