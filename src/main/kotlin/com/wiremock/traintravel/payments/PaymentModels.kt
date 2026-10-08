package com.wiremock.traintravel.payments

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonProperty
import com.wiremock.traintravel.common.LinksDto
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal

// --- Public API request models (snake_case via the global Jackson strategy) ---

data class PaymentRequest(
    @field:NotNull(message = "must not be null")
    @field:DecimalMin(value = "0", inclusive = false, message = "must be greater than 0")
    val amount: BigDecimal,
    @field:NotNull(message = "must not be null")
    val currency: String,
    @field:NotNull(message = "must not be null")
    val source: PaymentSource,
)

data class CheckoutRequest(
    @field:NotNull(message = "must not be null")
    @field:DecimalMin(value = "0", inclusive = false, message = "must be greater than 0")
    val amount: BigDecimal,
    @field:NotNull(message = "must not be null")
    val currency: String,
)

/** OneOf discriminator on the `object` field, exactly as the public spec defines it. */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "object")
@JsonSubTypes(
    JsonSubTypes.Type(value = CardSourceDto::class, name = "card"),
    JsonSubTypes.Type(value = BankAccountSourceDto::class, name = "bank_account"),
)
sealed class PaymentSource

data class CardSourceDto(
    val name: String,
    val number: String,
    val cvc: String,
    val expMonth: Int,
    val expYear: Int,
    val addressLine1: String? = null,
    val addressLine2: String? = null,
    val addressCity: String? = null,
    val addressCountry: String,
    val addressPostCode: String? = null,
) : PaymentSource() {
    @JsonProperty("object")
    val objectName: String = "card"
}

data class BankAccountSourceDto(
    val name: String,
    val number: String,
    val sortCode: String,
    val accountType: String,
    val bankName: String,
    val country: String,
) : PaymentSource() {
    @JsonProperty("object")
    val objectName: String = "bank_account"
}

// --- Public API response models ---

data class PaymentResponseDto(
    val id: String,
    val amount: BigDecimal,
    val currency: String,
    val source: PaymentSourceResponse,
    val status: String,
    val links: LinksDto,
)

sealed class PaymentSourceResponse

data class CardSourceResponse(
    val name: String,
    val number: String,
    val expMonth: Int,
    val expYear: Int,
    val addressCity: String? = null,
    val addressCountry: String? = null,
    val addressPostCode: String? = null,
) : PaymentSourceResponse() {
    @JsonProperty("object")
    val objectName: String = "card"
}

data class BankAccountSourceResponse(
    val name: String,
    val number: String,
    val sortCode: String,
    val accountType: String,
    val bankName: String,
    val country: String,
) : PaymentSourceResponse() {
    @JsonProperty("object")
    val objectName: String = "bank_account"
}

/** A Stripe Checkout session the browser should be redirected to. */
data class CheckoutSessionDto(
    val id: String,
    val url: String,
)

/** A payment taken on Stripe's hosted page: the gateway never sees the card, so there is no source. */
data class CheckoutPaymentDto(
    val id: String,
    val amount: BigDecimal,
    val currency: String,
    val status: String,
    val links: LinksDto,
)
