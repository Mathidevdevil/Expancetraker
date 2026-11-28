package com.spendwise.app

import android.content.SharedPreferences
import com.spendwise.app.data.local.SecurePrefsHelper
import com.spendwise.app.data.local.TextEncryptor
import com.spendwise.app.data.model.CategoryEntity
import com.spendwise.app.voice.VoiceTransactionParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

private class InMemorySharedPreferences : SharedPreferences {
    val store = mutableMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = store.toMutableMap()
    override fun getString(key: String?, defValue: String?): String? = (store[key] as? String) ?: defValue
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues
    override fun getInt(key: String?, defValue: Int): Int = (store[key] as? Int) ?: defValue
    override fun getLong(key: String?, defValue: Long): Long = (store[key] as? Long) ?: defValue
    override fun getFloat(key: String?, defValue: Float): Float = (store[key] as? Float) ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = (store[key] as? Boolean) ?: defValue
    override fun contains(key: String?): Boolean = store.containsKey(key)
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        private val removals = mutableSetOf<String>()
        private var clearAll = false

        override fun putString(key: String?, value: String?) = apply { if (key != null) pending[key] = value }
        override fun putStringSet(key: String?, values: MutableSet<String>?) = this
        override fun putInt(key: String?, value: Int) = apply { if (key != null) pending[key] = value }
        override fun putLong(key: String?, value: Long) = apply { if (key != null) pending[key] = value }
        override fun putFloat(key: String?, value: Float) = apply { if (key != null) pending[key] = value }
        override fun putBoolean(key: String?, value: Boolean) = apply { if (key != null) pending[key] = value }
        override fun remove(key: String?) = apply { if (key != null) removals.add(key) }
        override fun clear() = apply { clearAll = true }
        override fun commit(): Boolean { apply(); return true }
        override fun apply() {
            if (clearAll) store.clear()
            removals.forEach { store.remove(it) }
            store.putAll(pending)
        }
    }
}

private class DeterministicMockEncryptor : TextEncryptor {
    override fun encrypt(plainText: String): String = "enc::$plainText"
    override fun decrypt(cipherText: String): String? =
        if (cipherText.startsWith("enc::")) cipherText.removePrefix("enc::") else null
}

class SecurityRegressionTest {

    @Test
    fun migratesLegacyPlaintextPrefsToEncryptedPrefsWithoutLosingSession() {
        val legacy = InMemorySharedPreferences()
        legacy.edit()
            .putBoolean("is_logged_in", true)
            .putString("auth_user_id", "user-uuid-123")
            .putString("user_email", "mathi@example.com")
            .putString("user_name", "Mathi")
            .putString("user_initials", "MA")
            .putString("selected_currency", "INR")
            .putString("selected_currency_symbol", "₹")
            .putString("theme_mode", "dark")
            .putString("voice_language", "ta-IN")
            .apply()

        val secure = InMemorySharedPreferences()
        val helper = SecurePrefsHelper(secure, legacy, DeterministicMockEncryptor())
        helper.migrateFromLegacyIfNeeded()

        // Existing user remains logged in with identical values
        assertTrue(helper.getBoolean("is_logged_in", false))
        assertEquals("user-uuid-123", helper.getString("auth_user_id"))
        assertEquals("mathi@example.com", helper.getString("user_email"))
        assertEquals("Mathi", helper.getString("user_name"))
        assertEquals("INR", helper.getString("selected_currency"))
        assertEquals("ta-IN", helper.getString("voice_language"))

        // Stored values in securePrefs are encrypted
        assertEquals("enc::user-uuid-123", secure.getString("auth_user_id", null))
        assertEquals("enc::mathi@example.com", secure.getString("user_email", null))

        // Legacy plaintext keys were deleted
        assertFalse(legacy.contains("is_logged_in"))
        assertFalse(legacy.contains("auth_user_id"))
        assertFalse(legacy.contains("user_email"))

        // Idempotency: running migration again does not alter or lose values
        helper.putString("user_name", "Updated Mathi")
        helper.migrateFromLegacyIfNeeded()
        assertEquals("Updated Mathi", helper.getString("user_name"))
    }

    @Test
    fun edgeFunctionSmartParserSkipsNetworkWithoutUserJwt() = runBlocking {
        val cats = listOf(
            CategoryEntity(id = "cat-food", name = "Food & Dining", type = "expense", icon = "Utensils", color = "#EF4444", isDefault = true)
        )
        // With null/blank accessToken, smart parser never attempts unauthenticated network request
        val res = VoiceTransactionParser.parseWithOptionalSmartFallback(
            transcript = "spent 320 on swiggy lunch",
            categories = cats,
            supabaseUrl = "https://ahzrvlkrgowgsfuirlae.supabase.co",
            supabaseKey = "public-anon-key",
            accessToken = null
        )
        assertEquals("expense", res.type)
        assertEquals(320.0, res.amount!!, 0.01)
        assertEquals("Food & Dining", res.categoryName)
        assertFalse(res.usedSmartParser)
    }
}
