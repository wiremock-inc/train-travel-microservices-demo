package com.wiremock.traintravel.config

import com.wiremock.traintravel.common.Problem
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
class SecurityConfig {

    /**
     * CORS for the bundled UI dev server (http://localhost:3000) - the browser calls
     * this API cross-origin, and the Authorization header makes every call a preflight.
     */
    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource =
        UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration(
                "/**",
                CorsConfiguration().apply {
                    allowedOrigins = listOf("http://localhost:3000")
                    allowedMethods = listOf("GET", "POST", "DELETE", "OPTIONS")
                    allowedHeaders = listOf("*")
                },
            )
        }

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http.csrf { it.disable() }
            .cors { }
            .authorizeHttpRequests { auth ->
                auth.requestMatchers(HttpMethod.POST, "/bookings/**").hasAuthority("SCOPE_write")
                    .requestMatchers(HttpMethod.DELETE, "/bookings/**").hasAuthority("SCOPE_write")
                    .anyRequest().hasAuthority("SCOPE_read")
            }
            .exceptionHandling { handling ->
                handling.authenticationEntryPoint(ProblemAuthenticationEntryPoint())
                    .accessDeniedHandler(ProblemAccessDeniedHandler())
            }
            .oauth2ResourceServer { server -> server.jwt() }
        return http.build()
    }
}

/** Renders 401s as RFC 7807 problem+json instead of Spring's empty default body. */
class ProblemAuthenticationEntryPoint : AuthenticationEntryPoint {
    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException,
    ) {
        response.status = 401
        response.addHeader("WWW-Authenticate", "Bearer")
        response.contentType = "application/problem+json"
        response.writer.write(
            Problem.toJson(Problem.of(401, "You do not have the necessary permissions.", request.requestURI)),
        )
    }
}

/** Renders 403s (scope failures) as RFC 7807 problem+json. */
class ProblemAccessDeniedHandler : AccessDeniedHandler {
    override fun handle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        accessDeniedException: org.springframework.security.access.AccessDeniedException,
    ) {
        response.status = 403
        response.contentType = "application/problem+json"
        response.writer.write(
            Problem.toJson(Problem.of(403, "Access is forbidden with the provided credentials.", request.requestURI)),
        )
    }
}