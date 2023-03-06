package ru.dmkuranov.temporaltests.util

import org.springframework.boot.autoconfigure.cache.Cache2kBuilderCustomizer
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock
import java.util.concurrent.TimeUnit

@Configuration
@EnableCaching
class ContextConfiguration {

    @Bean
    fun clock(): Clock = Clock.systemDefaultZone()

    @Bean
    fun cache2kBuilderCustomizer() =
        Cache2kBuilderCustomizer { builder ->
            builder!!
                .expiryPolicy { _, _, startTime, _ -> startTime + TimeUnit.MILLISECONDS.toMillis(CACHE_EXPIRATION_MS) }
                .sharpExpiry(true)
                .entryCapacity(CACHE_CAPACITY_MS)
        }

    companion object {
        const val CACHE_EXPIRATION_MS = 100L
        const val CACHE_CAPACITY_MS = 100L
    }
}
