package com.wiremock.traintravel.common

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper

/** RFC 7807 problem detail, the error body shape of the public Train Travel API. */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class Problem(
    val type: String,
    val title: String,
    val status: Int,
    val detail: String,
    val instance: String? = null,
) {
    companion object {
        private val mapper = ObjectMapper()

        private val types: Map<Int, Pair<String, String>> = mapOf(
            400 to ("https://example.com/errors/bad-request" to "Bad Request"),
            401 to ("https://example.com/errors/unauthorized" to "Unauthorized"),
            403 to ("https://example.com/errors/forbidden" to "Forbidden"),
            404 to ("https://example.com/errors/not-found" to "Not Found"),
            409 to ("https://example.com/errors/conflict" to "Conflict"),
            429 to ("https://example.com/errors/too-many-requests" to "Too Many Requests"),
            500 to ("https://example.com/errors/internal-server-error" to "Internal Server Error"),
        )

        fun of(status: Int, detail: String, instance: String?): Problem {
            val (type, title) = types[status] ?: ("about:blank" to "Error")
            return Problem(type, title, status, detail, instance)
        }

        fun toJson(problem: Problem): String = mapper.writeValueAsString(problem)
    }
}