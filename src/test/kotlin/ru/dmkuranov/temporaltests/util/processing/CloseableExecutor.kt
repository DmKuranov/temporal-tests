package ru.dmkuranov.temporaltests.util.processing

import org.springframework.core.task.AsyncTaskExecutor
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.io.Closeable

class CloseableExecutor(
    private val concurrency: Int = 10,
    private val processor: ThreadPoolTaskExecutor = ThreadPoolTaskExecutor()
) : Closeable, AsyncTaskExecutor by processor {
    init {
        with(processor) {
            corePoolSize = concurrency
            maxPoolSize = concurrency
            queueCapacity = concurrency * 3
            setWaitForTasksToCompleteOnShutdown(true)
            setAwaitTerminationSeconds(10)
            initialize()
        }
    }

    override fun close() {
        processor.shutdown()
    }
}
