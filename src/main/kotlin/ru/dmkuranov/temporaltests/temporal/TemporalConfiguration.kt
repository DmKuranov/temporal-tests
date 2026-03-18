package ru.dmkuranov.temporaltests.temporal

import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.temporal.activity.ActivityOptions
import io.temporal.common.RetryOptions
import io.temporal.common.converter.DataConverter
import io.temporal.common.converter.DefaultDataConverter
import io.temporal.common.converter.JacksonJsonPayloadConverter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

@Configuration
class TemporalConfiguration {

    @Bean("mainDataConverter")
    fun mainDataConverter(): DataConverter =
        JacksonJsonPayloadConverter.newDefaultObjectMapper()
            .also { it.registerKotlinModule() }
            .let { JacksonJsonPayloadConverter(it) }
            .let {
                DefaultDataConverter.newDefaultInstance()
                    .withPayloadConverterOverrides(it)
            }

    companion object {
        const val NAMESPACE_NAME = "default"
        const val TASK_QUEUE_NAME = "OrderTaskQueue_v1"
        private val RETRY_OPTIONS_DEFAULT = RetryOptions.newBuilder()
            .setInitialInterval(Duration.ofMillis(50))
            .setBackoffCoefficient(1.2)
            .setMaximumInterval(Duration.ofMillis(2000))
            .setDoNotRetry(
                TemporalNonRetryableException::class.java.name
            )
            .build()!!
        val ACTIVITY_OPTIONS_DEFAULT = ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofSeconds(10))
            .setRetryOptions(RETRY_OPTIONS_DEFAULT)
            .build()!!
    }
}
