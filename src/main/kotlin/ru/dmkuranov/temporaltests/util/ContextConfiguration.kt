package ru.dmkuranov.temporaltests.util

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration
class ContextConfiguration {

    @Bean
    fun clock(): Clock = Clock.systemDefaultZone()
}
