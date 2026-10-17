package dev.akram.reelcount.sync

import dev.akram.reelcount.BuildConfig

object SupabaseConfig {
    val url: String = BuildConfig.SUPABASE_URL.trim().trimEnd('/')
    val anonKey: String = BuildConfig.SUPABASE_ANON_KEY.trim()

    fun isConfigured(): Boolean = url.startsWith("https://") && anonKey.length > 20
}
