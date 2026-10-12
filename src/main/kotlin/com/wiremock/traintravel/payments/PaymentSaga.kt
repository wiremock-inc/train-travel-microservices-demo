package com.wiremock.traintravel.payments

import com.wiremock.traintravel.bookings.BookingsService
import com.wiremock.traintravel.common.BadRequestException
import com.wiremock.traintravel.common.LinksDto
import com.wiremock.traintravel.config.AppProperties
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Year

private val currencies = setOf("bam", "bgn", "chf", "eur", "gbp", "nok", "sek", "try")

internal fun requireSupportedCurrency(currency: String) {
    if (currency !in currencies) {
        throw BadRequestException("Field 'currency' must be one of: ${currencies.sorted().joinToString(", ")}.")
    }
}

/**
 * The payment saga: validate the hold -> SOAP ProcessPayment -> on APPROVED confirm the
 * reservation. A DECLINED payment leaves the booking HELD and surfaces as a public 400.
 */
@Service
class PaymentSaga(
    private val bookingsService: BookingsService,
    private val payRailClient: PayRailClient,
    private val props: AppProperties,
) {

    fun pay(userId: String, bookingId: String, request: PaymentRequest): PaymentResponseDto {
        val currency = request.currency.lowercase()
        validate(request, currency)

        val reservation = bookingsService.getReservation(userId, bookingId)
        if (reservation.status == "CONFIRMED") {
            throw BadRequestException("Booking has already been paid.")
        }

        // Two decimal places: PayRail echoes the amount back from the envelope
        val amount = request.amount.setScale(2)
        val approval = payRailClient.processPayment(bookingId, amount, currency.uppercase(), request.source)

        bookingsService.confirm(userId, bookingId)
        return toResponse(bookingId, request, currency, approval)
    }

    private fun validate(request: PaymentRequest, currency: String) {
        requireSupportedCurrency(currency)
        when (val source = request.source) {
            is CardSourceDto -> {
                if (source.name.isBlank()) throw BadRequestException("Field 'source.name' must not be blank.")
                if (!source.number.matches(Regex("^[0-9]{12,19}$"))) {
                    throw BadRequestException("Field 'source.number' must be 12-19 digits.")
                }
                if (!source.cvc.matches(Regex("^[0-9]{3,4}$"))) {
                    throw BadRequestException("Field 'source.cvc' must be 3 or 4 digits.")
                }
                if (source.expMonth !in 1..12) throw BadRequestException("Field 'source.exp_month' must be between 1 and 12.")
                if (source.expYear < Year.now().value) throw BadRequestException("Field 'source.exp_year' is in the past.")
                if (!source.addressCountry.matches(Regex("^[A-Za-z]{2}$"))) {
                    throw BadRequestException("Field 'source.address_country' must be a 2-letter country code.")
                }
            }
            is BankAccountSourceDto -> {
                if (source.name.isBlank()) throw BadRequestException("Field 'source.name' must not be blank.")
                if (!source.number.matches(Regex("^[0-9]{6,12}$"))) {
                    throw BadRequestException("Field 'source.number' must be 6-12 digits.")
                }
                if (!source.sortCode.matches(Regex("^[0-9]{6}$"))) {
                    throw BadRequestException("Field 'source.sort_code' must be 6 digits.")
                }
                if (source.accountType !in setOf("individual", "company")) {
                    throw BadRequestException("Field 'source.account_type' must be 'individual' or 'company'.")
                }
                if (source.bankName.isBlank()) throw BadRequestException("Field 'source.bank_name' must not be blank.")
                if (!source.country.matches(Regex("^[A-Za-z]{2}$"))) {
                    throw BadRequestException("Field 'source.country' must be a 2-letter country code.")
                }
            }
        }
    }

    private fun toResponse(
        bookingId: String,
        request: PaymentRequest,
        currency: String,
        approval: PayRailApproval,
    ): PaymentResponseDto {
        // The gateway never emits a raw PAN: source.number is always PayRail's masked value
        val sourceResponse: PaymentSourceResponse = when (val source = request.source) {
            is CardSourceDto -> CardSourceResponse(
                name = source.name,
                number = approval.maskedCardNumber ?: "************",
                expMonth = source.expMonth,
                expYear = source.expYear,
                addressCity = source.addressCity,
                addressCountry = source.addressCountry,
                addressPostCode = source.addressPostCode,
            )
            is BankAccountSourceDto -> BankAccountSourceResponse(
                name = source.name,
                number = approval.maskedAccountNumber ?: "**********",
                sortCode = source.sortCode,
                accountType = source.accountType,
                bankName = source.bankName,
                country = source.country,
            )
        }
        return PaymentResponseDto(
            id = approval.transactionId,
            amount = approval.authorisedAmount ?: request.amount,
            currency = currency,
            source = sourceResponse,
            status = "succeeded",
            links = LinksDto(booking = "${props.publicBaseUrl}/bookings/$bookingId"),
        )
    }
}