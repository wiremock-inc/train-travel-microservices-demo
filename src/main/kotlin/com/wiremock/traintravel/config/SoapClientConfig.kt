package com.wiremock.traintravel.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.oxm.jaxb.Jaxb2Marshaller
import org.springframework.ws.client.core.WebServiceTemplate
import org.springframework.ws.transport.http.HttpUrlConnectionMessageSender

@Configuration
class SoapClientConfig(private val props: AppProperties) {

    @Bean
    fun payRailMarshaller(): Jaxb2Marshaller = Jaxb2Marshaller().apply {
        setContextPath("com.wiremock.traintravel.payrail.generated")
    }

    @Bean
    fun payRailWebServiceTemplate(marshaller: Jaxb2Marshaller): WebServiceTemplate {
        val sender = HttpUrlConnectionMessageSender().apply {
            setConnectionTimeout(props.downstream.connectTimeout)
            setReadTimeout(props.downstream.readTimeout)
        }
        return WebServiceTemplate(marshaller).apply {
            defaultUri = props.payments.baseUrl
            setMessageSender(sender)
        }
    }
}