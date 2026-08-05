package com.ivy.data.remote.supabase

import arrow.core.Either
import arrow.core.raise.catch
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import javax.inject.Inject

/**
 * Thin wrapper over Supabase's auto-generated REST API (PostgREST), reached with plain HTTP
 * instead of the supabase-kt SDK to reuse the app's existing Ktor client/config.
 *
 * Auth model: no Supabase Auth/user accounts - every row carries an `owner_id` column set to
 * a random id generated once per app install (see `CloudSyncSettings.ownerId`), and Supabase
 * Row Level Security policies should restrict access using the anon key + `owner_id` filter.
 * This is enough to keep one installation's data from colliding with another's when they share
 * the same Supabase project, but it is NOT strong authentication - anyone with the anon key and
 * a guessed owner_id could read/write those rows. Fine for personal use; if that matters to you,
 * put Supabase Auth in front of this instead.
 */
data class SupabaseConfig(
    val url: String,
    val anonKey: String,
)

class SupabaseRestClient @Inject constructor(
    @PublishedApi internal val ktorClient: dagger.Lazy<HttpClient>,
) {
    /** Upsert (insert-or-update by primary key) a batch of rows into [table]. */
    suspend inline fun <reified T> upsert(
        config: SupabaseConfig,
        table: String,
        rows: List<T>,
    ): Either<String, Unit> = catch({
        if (rows.isNotEmpty()) {
            ktorClient.get().post("${restUrl(config.url)}/$table") {
                authHeaders(config)
                headers {
                    append("Prefer", "resolution=merge-duplicates,return=minimal")
                }
                contentType(ContentType.Application.Json)
                setBody(rows)
            }
        }
        Either.Right(Unit)
    }) { e ->
        Either.Left(e.message ?: "Failed to upsert into Supabase table '$table'")
    }

    /** Fetch every row in [table] belonging to [ownerId]. */
    suspend inline fun <reified T> fetchAll(
        config: SupabaseConfig,
        table: String,
        ownerId: String,
    ): Either<String, List<T>> = catch({
        val response = ktorClient.get().get("${restUrl(config.url)}/$table") {
            authHeaders(config)
            parameter("owner_id", "eq.$ownerId")
            parameter("select", "*")
        }
        Either.Right(response.body<List<T>>())
    }) { e ->
        Either.Left(e.message ?: "Failed to fetch from Supabase table '$table'")
    }

    @PublishedApi
    internal fun restUrl(baseUrl: String) = "${baseUrl.trimEnd('/')}/rest/v1"

    @PublishedApi
    internal fun HttpRequestBuilder.authHeaders(config: SupabaseConfig) {
        headers {
            append("apikey", config.anonKey)
            append("Authorization", "Bearer ${config.anonKey}")
        }
    }
}
