package com.wiremock.traintravel.payments

import com.wiremock.traintravel.common.DownstreamException
import com.wiremock.traintravel.common.PayRailValidationException
import com.wiremock.traintravel.common.PaymentDeclinedException
import com.wiremock.traintravel.payrail.generated.BankAccount
import com.wiremock.traintravel.payrail.generated.Card
import com.wiremock.traintravel.payrail.generated.ObjectFactory
import com.wiremock.traintravel.payrail.generated.PaymentMethod
import com.wiremock.traintravel.payrail.generated.ProcessPaymentRequest
import com.wiremock.traintravel.payrail.generated.ProcessPaymentResponse
import org.springframework.stereotype.Component
import org.springframework.ws.client.core.WebServiceTemplate
import org.springframework.ws.soap.SoapFaultException
import org.springframework.ws.soap.client.core.SoapActionCallback
import java.math.BigDecimal

/** What PayRail told us about an approved payment. */
data class PayRailApproval(
    val transactionId: String,
    val authorisedAmount: BigDecimal?,
    val maskedCardNumber: String?,
    val maskedAccountNumber: String?,
    val processedAt: String?,
)

/**
 * SOAP client for the PayRail payment gateway. JAXB classes are generated from
 * payrail.wsdl at build time.
 */
@Component
class PayRailClient(private val template: WebServiceTemplate) {

    fun processPayment(
        merchantReference: String,
        amount: BigDecimal,
        currencyCode: String,
        source: PaymentSource,
    ): PayRailApproval {
        val factory = ObjectFactory()
        val request = factory.createProcessPaymentRequest().apply {
            this.merchantId = "TRAINTRAVEL-EU"
            this.merchantReference = merchantReference
            this.amount = amount
            this.currencyCode = currencyCode
            when (source) {
                is CardSourceDto -> paymentMethod = PaymentMethod().apply {
                    card = Card().apply {
                        cardholderName = source.name
                        cardNumber = source.number
                        expiryMonth = source.expMonth
                        expiryYear = source.expYear
                        securityCode = source.cvc
                        source.addressPostCode?.let { billingPostCode = it }
                    }
                }
                is BankAccountSourceDto -> paymentMethod = PaymentMethod().apply {
                    bankAccount = BankAccount().apply {
                        accountName = source.name
                        accountNumber = source.number
                        sortCode = source.sortCode
                        accountType = source.accountType
                        bankName = source.bankName
                        countryCode = source.country
                    }
                }
            }
        }

        return try {
            val response = template.marshalSendAndReceive(
                request,
                SoapActionCallback("processPayment"),
            ) as ProcessPaymentResponse
            when (response.responseCode) {
                "APPROVED" -> PayRailApproval(
                    transactionId = response.transactionId,
                    authorisedAmount = response.authorisedAmount,
                    maskedCardNumber = response.maskedCardNumber,
                    maskedAccountNumber = response.maskedAccountNumber,
                    processedAt = response.processedAt,
                )
                else -> throw PaymentDeclinedException(response.declineReason ?: "declined")
            }
        } catch (fault: SoapFaultException) {
            // SoapFaultException's message is the SOAP fault string
            val faultText = fault.message ?: "unknown SOAP fault"
            if (faultText.contains("VALIDATION")) {
                throw PayRailValidationException("The payment provider rejected the request: $faultText")
            }
            throw DownstreamException("PayRail SOAP fault: $faultText")
        }
    }
}