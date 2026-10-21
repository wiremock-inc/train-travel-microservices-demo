package com.wiremock.traintravel

import com.wiremock.traintravel.config.InsecureTls
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class TrainTravelApplication

fun main(args: Array<String>) {
    InsecureTls.trustAllCertificates()
    runApplication<TrainTravelApplication>(*args)
}