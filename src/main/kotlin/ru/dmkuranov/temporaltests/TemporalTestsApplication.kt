package ru.dmkuranov.temporaltests

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class TemporalTestsApplication

fun main(args: Array<String>) {
    runApplication<TemporalTestsApplication>(*args)
}
