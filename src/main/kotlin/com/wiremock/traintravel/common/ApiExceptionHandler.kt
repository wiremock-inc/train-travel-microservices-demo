package com.wiremock.traintravel.common

import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClientResponseException

/**
 * Single mapper from internal failures to the public RFC 7807 error contract.
 * Downstream bodies are never surfaced verbatim - only their HTTP status is logged.
 */
@RestControllerAdvice
class ApiExceptionHandler {

    private val log = LoggerFactory.getLogger(ApiExceptionHandler::class.java)

    @ExceptionHandler(BadRequestException::class, PayRailValidationException::class)
    fun badRequest(ex: RuntimeException, request: HttpServletRequest): ResponseEntity<Problem> =
        problem(400, ex.message ?: "The request is invalid or missing required parameters.", request)

    @ExceptionHandler(PaymentDeclinedException::class)
    fun paymentDeclined(ex: PaymentDeclinedException, request: HttpServletRequest): ResponseEntity<Problem> =
        problem(400, ex.message ?: "Payment was declined.", request)

    @ExceptionHandler(NotFoundException::class, BookingNotFoundException::class)
    fun notFound(ex: RuntimeException, request: HttpServletRequest): ResponseEntity<Problem> =
        problem(404, ex.message ?: "Resource not found.", request)

    @ExceptionHandler(DownstreamException::class)
    fun downstream(ex: DownstreamException, request: HttpServletRequest): ResponseEntity<Problem> {
        log.warn("Downstream failure: {}", ex.message)
        return problem(500, "An unexpected error occurred.", request)
    }

    @ExceptionHandler(RestClientResponseException::class)
    fun downstreamStatus(ex: RestClientResponseException, request: HttpServletRequest): ResponseEntity<Problem> {
        log.warn("Downstream returned {}: {}", ex.statusCode, ex.responseBodyAsString.take(200))
        return problem(500, "An unexpected error occurred.", request)
    }

    @ExceptionHandler(ResourceAccessException::class)
    fun downstreamTimeout(ex: ResourceAccessException, request: HttpServletRequest): ResponseEntity<Problem> {
        log.warn("Downstream unreachable: {}", ex.message)
        return problem(500, "An unexpected error occurred.", request)
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun validation(ex: MethodArgumentNotValidException, request: HttpServletRequest): ResponseEntity<Problem> {
        val detail = ex.bindingResult.fieldErrors.joinToString("; ") { "${it.field}: ${it.defaultMessage}" }
        return problem(400, detail.ifBlank { "The request is invalid or missing required parameters." }, request)
    }

    @ExceptionHandler(ConstraintViolationException::class)
    fun constraint(ex: ConstraintViolationException, request: HttpServletRequest): ResponseEntity<Problem> {
        val detail = ex.constraintViolations.joinToString("; ") { "${it.propertyPath}: ${it.message}" }
        return problem(400, detail, request)
    }

    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun missingParam(ex: MissingServletRequestParameterException, request: HttpServletRequest): ResponseEntity<Problem> =
        problem(400, "Missing required parameter: ${ex.parameterName}.", request)

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun unreadable(ex: HttpMessageNotReadableException, request: HttpServletRequest): ResponseEntity<Problem> =
        problem(400, "Malformed request body.", request)

    @ExceptionHandler(Exception::class)
    fun unexpected(ex: Exception, request: HttpServletRequest): ResponseEntity<Problem> {
        log.error("Unhandled exception", ex)
        return problem(500, "An unexpected error occurred.", request)
    }

    private fun problem(status: Int, detail: String, request: HttpServletRequest): ResponseEntity<Problem> =
        ResponseEntity.status(HttpStatus.valueOf(status))
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(Problem.of(status, detail, request.requestURI))
}