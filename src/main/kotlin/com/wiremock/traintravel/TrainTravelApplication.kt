package com.wiremock.traintravel

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class TrainTravelApplication

fun main(args: Array<String>) {
    runApplication<TrainTravelApplication>(*args)
}