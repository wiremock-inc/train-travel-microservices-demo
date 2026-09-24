package com.wiremock.traintravel.config

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.MediaType
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.net.http.HttpClient
import java.time.Duration

/**
 * Builds [RestClient]s for downstream services. Downstream payloads are camelCase, so every
 * client gets its own camelCase ObjectMapper (the public API's global mapper is snake_case;
 * callers can override, e.g. the webhook notifier posts public snake_case bodies).
 */
@Component
class RestClientFactory(private val props: AppProperties) {

    // Jackson's default naming strategy IS lowerCamelCase, which matches the downstream
    // services' conventions - no explicit strategy needed. This mapper exists to escape
    // the public API's global snake_case mapper.
    private val downstreamMapper: ObjectMapper = ObjectMapper()
        .findAndRegisterModules()

    fun create(
        baseUrl: String,
        readTimeout: Duration = props.downstream.readTimeout,
        mapper: ObjectMapper = downstreamMapper,
    ): RestClient {
        val httpClient = HttpClient.newBuilder()
            .connectTimeout(props.downstream.connectTimeout)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build()
        val requestFactory = JdkClientHttpRequestFactory(httpClient).apply {
            setReadTimeout(readTimeout)
        }
        val converter = MappingJackson2HttpMessageConverter(mapper).apply {
            supportedMediaTypes = listOf(MediaType.APPLICATION_JSON)
        }
        return RestClient.builder()
            .baseUrl(baseUrl)
            .requestFactory(requestFactory)
            .messageConverters { converters ->
                // Swap the default Jackson converter (snake_case global mapper) for the
                // camelCase one, keeping String/form converters intact
                converters.removeIf { it is MappingJackson2HttpMessageConverter }
                converters.add(0, converter)
            }
            .build()
    }
}