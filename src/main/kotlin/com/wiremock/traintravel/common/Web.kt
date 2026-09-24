package com.wiremock.traintravel.common

import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import java.net.URLEncoder
import java.util.UUID

/** Collection wrapper matching the public API's HATEOAS envelope. */
data class CollectionDto<T>(
    val data: List<T>,
    val links: LinksDto,
)

data class LinksDto(
    val self: String? = null,
    val next: String? = null,
    val prev: String? = null,
    val origin: String? = null,
    val destination: String? = null,
    val booking: String? = null,
)

/** Validates the public API's page/limit defaults (page >= 1, limit 1..100). */
fun validatePagination(page: Int, limit: Int) {
    if (page < 1) throw BadRequestException("Parameter 'page' must be >= 1.")
    if (limit < 1 || limit > 100) throw BadRequestException("Parameter 'limit' must be between 1 and 100.")
}

/** Parses a path segment that must be a UUID. */
fun requireUuid(value: String, name: String): UUID =
    try {
        UUID.fromString(value)
    } catch (e: IllegalArgumentException) {
        throw BadRequestException("Parameter '$name' must be a UUID.")
    }

/**
 * Builds self/next/prev links for a paginated collection. `next` is only present when
 * another page exists, `prev` only when past the first page.
 */
fun paginationLinks(
    publicBaseUrl: String,
    path: String,
    params: Map<String, String?>,
    page: Int,
    limit: Int,
    total: Int,
): LinksDto {
    fun url(p: Int): String {
        val query = (params + mapOf("page" to p.toString(), "limit" to limit.toString()))
            .filterValues { it != null }
            .mapValues { it.value!! }
            .entries
            .joinToString("&") { (k, v) -> "$k=${encode(v)}" }
        return "$publicBaseUrl$path?$query"
    }
    val links = mutableMapOf<String, String>()
    links["self"] = url(page)
    if (page * limit < total) links["next"] = url(page + 1)
    if (page > 1) links["prev"] = url(page - 1)
    return LinksDto(self = links["self"], next = links["next"], prev = links["prev"])
}

private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8)

/** The authenticated user's identity (JWT `sub`) - forwarded to the bookings service as X-User-Id. */
fun currentUserId(auth: JwtAuthenticationToken): String =
    auth.token.claims["sub"]?.toString()
        ?: throw BadRequestException("The access token has no subject claim.")