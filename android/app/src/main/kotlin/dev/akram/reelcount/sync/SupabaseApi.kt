package dev.akram.reelcount.sync

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class AuthSession(
    val accessToken: String,
    val refreshToken: String,
    val userId: String,
    val email: String,
    val expiresAtEpochMs: Long,
)

class SupabaseApi(
    private val baseUrl: String,
    private val anonKey: String,
    private val client: OkHttpClient =
        OkHttpClient
            .Builder()
            .callTimeout(30, TimeUnit.SECONDS)
            .build(),
) {
    fun signIn(
        email: String,
        password: String,
    ): AuthSession = passwordGrant("/auth/v1/token?grant_type=password", email, password)

    fun signUp(
        email: String,
        password: String,
    ): AuthSession? {
        val body =
            JSONObject()
                .put("email", email)
                .put("password", password)
                .toString()
        val json = postJson("/auth/v1/signup", body, bearer = null)
        val token = json.optString("access_token")
        if (token.isBlank()) return null
        return sessionFrom(json, email)
    }

    fun refresh(refreshToken: String): AuthSession {
        val body = JSONObject().put("refresh_token", refreshToken).toString()
        val json = postJson("/auth/v1/token?grant_type=refresh_token", body, bearer = null)
        return sessionFrom(json, json.optJSONObject("user")?.optString("email").orEmpty())
    }

    fun upsertDaily(
        token: String,
        rows: JSONArray,
    ) {
        if (rows.length() == 0) return
        postJson(
            "/rest/v1/daily_counts?on_conflict=user_id,device_id,day,app",
            rows.toString(),
            bearer = token,
            prefer = "resolution=merge-duplicates,return=minimal",
        )
    }

    fun upsertHourly(
        token: String,
        rows: JSONArray,
    ) {
        if (rows.length() == 0) return
        postJson(
            "/rest/v1/hourly_counts?on_conflict=user_id,device_id,day,hour,app",
            rows.toString(),
            bearer = token,
            prefer = "resolution=merge-duplicates,return=minimal",
        )
    }

    fun updateProfile(
        token: String,
        userId: String,
        dailyLimit: Int,
        locale: String,
    ) {
        val body =
            JSONObject()
                .put("daily_limit", dailyLimit)
                .put("locale", locale)
                .toString()
        patchJson("/rest/v1/profiles?id=eq.$userId", body, token)
    }

    private fun passwordGrant(
        path: String,
        email: String,
        password: String,
    ): AuthSession {
        val body =
            JSONObject()
                .put("email", email)
                .put("password", password)
                .toString()
        return sessionFrom(postJson(path, body, bearer = null), email)
    }

    private fun sessionFrom(
        json: JSONObject,
        fallbackEmail: String,
    ): AuthSession {
        val access = json.optString("access_token")
        val refresh = json.optString("refresh_token")
        val user = json.optJSONObject("user")
        val userId = user?.optString("id").orEmpty()
        if (access.isBlank() || refresh.isBlank() || userId.isBlank()) {
            val message = json.optString("msg").ifBlank { json.optString("error_description") }
            throw IllegalStateException(message.ifBlank { "Auth response had no session" })
        }
        val expiresIn = json.optLong("expires_in", 3600L)
        return AuthSession(
            accessToken = access,
            refreshToken = refresh,
            userId = userId,
            email = user.optString("email").ifBlank { fallbackEmail },
            expiresAtEpochMs = System.currentTimeMillis() + expiresIn * 1000L,
        )
    }

    private fun postJson(
        path: String,
        body: String,
        bearer: String?,
        prefer: String? = null,
    ): JSONObject {
        val request =
            Request
                .Builder()
                .url(baseUrl + path)
                .headers(bearer, prefer)
                .post(body.toRequestBody(JSON))
                .build()
        return execute(request)
    }

    private fun patchJson(
        path: String,
        body: String,
        bearer: String,
    ) {
        val request =
            Request
                .Builder()
                .url(baseUrl + path)
                .headers(bearer, "return=minimal")
                .patch(body.toRequestBody(JSON))
                .build()
        execute(request)
    }

    private fun Request.Builder.headers(
        bearer: String?,
        prefer: String?,
    ): Request.Builder {
        header("apikey", anonKey)
        header("Content-Type", "application/json")
        if (!bearer.isNullOrBlank()) {
            header("Authorization", "Bearer $bearer")
        } else {
            header("Authorization", "Bearer $anonKey")
        }
        if (!prefer.isNullOrBlank()) header("Prefer", prefer)
        return this
    }

    private fun errorMessage(
        text: String,
        code: Int,
    ): String {
        val json = runCatching { JSONObject(text) }.getOrNull()
        if (json != null) {
            val msg =
                listOf("msg", "message", "error_description", "error")
                    .asSequence()
                    .map { json.optString(it) }
                    .firstOrNull { it.isNotBlank() && it != "null" }
            if (!msg.isNullOrBlank()) return msg.take(180)
        }
        return text.take(180).ifBlank { "HTTP $code" }
    }

    private fun execute(request: Request): JSONObject {
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException(errorMessage(text, response.code))
            }
            if (text.isBlank()) return JSONObject()
            return if (text.trimStart().startsWith("{")) JSONObject(text) else JSONObject()
        }
    }

    companion object {
        private val JSON = "application/json".toMediaType()
    }
}
