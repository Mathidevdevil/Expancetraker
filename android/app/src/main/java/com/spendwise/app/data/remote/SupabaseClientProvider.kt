package com.spendwise.app.data.remote

import android.content.Context
import com.spendwise.app.data.model.*
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseClientProvider @Inject constructor() {

    // Default configuration (placeholder until user inputs their own keys)
    var supabaseUrl: String = "https://placeholder-spendwise.supabase.co"
    var supabaseKey: String = "placeholder-anon-key"

    private var _client: SupabaseClient? = null

    val isConfigured: Boolean
        get() = supabaseUrl.isNotEmpty() && !supabaseUrl.contains("placeholder") && !supabaseKey.contains("placeholder")

    fun getClient(): SupabaseClient {
        return _client ?: synchronized(this) {
            _client ?: createSupabaseClient(
                supabaseUrl = supabaseUrl,
                supabaseKey = supabaseKey
            ) {
                install(Auth)
                install(Postgrest)
                install(Realtime)
            }.also { _client = it }
        }
    }

    suspend fun syncTransactions(pending: List<TransactionEntity>): List<TransactionEntity> {
        if (!isConfigured) return pending
        return withContext(Dispatchers.IO) {
            try {
                // Postgrest upload
                pending
            } catch (e: Exception) {
                pending
            }
        }
    }
}
