package ru.dmkuranov.temporaltests

import mu.KotlinLogging
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.test.context.ActiveProfiles
import ru.dmkuranov.temporaltests.core.charge.ChargeService
import ru.dmkuranov.temporaltests.core.charge.db.CustomerChargeRepository
import ru.dmkuranov.temporaltests.core.customerorder.CustomerOrderService
import ru.dmkuranov.temporaltests.core.processing.FulfillmentService
import ru.dmkuranov.temporaltests.core.stock.StockService
import ru.dmkuranov.temporaltests.core.stock.dto.ProductDto
import ru.dmkuranov.temporaltests.util.OrderCreateRequestSupplierBuilder
import ru.dmkuranov.temporaltests.util.SnapshotService
import ru.dmkuranov.temporaltests.util.invocation.TransactionalInvoker
import ru.dmkuranov.temporaltests.util.processing.ProcessingException
import java.math.BigDecimal
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.random.Random

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = ["spring.main.allow-bean-definition-overriding = true"])
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(value = MethodOrderer.OrderAnnotation::class)
@Suppress("UnnecessaryAbstractClass") // abstract members not required
abstract class AbstractIntegrationTest {
    @Autowired
    protected lateinit var stockService: StockService

    @SpyBean
    protected lateinit var customerOrderService: CustomerOrderService

    @SpyBean
    protected lateinit var fulfillmentService: FulfillmentService

    @SpyBean
    protected lateinit var chargeService: ChargeService

    @Autowired
    protected lateinit var snapshotService: SnapshotService

    @Autowired
    protected lateinit var customerChargeRepository: CustomerChargeRepository

    @Autowired
    protected lateinit var orderCreateRequestSupplierBuilder: OrderCreateRequestSupplierBuilder

    @Autowired
    protected lateinit var transactionalInvoker: TransactionalInvoker<Any?>

    fun createProduct(quantityStock: Long): ProductDto {
        return stockService.createProduct(quantityStock, nextRandomPrice())
    }

    protected fun createProducts(count: Int, initialStockQuantity: Long = 10000L) =
        (1..count).asSequence().map { createProduct(initialStockQuantity) }.toList()

    protected fun <ExecutionCredential_T> executeWorkflow(
        concurrency: Int,
        executionStarter: () -> ExecutionCredential_T,
        executionLimit: Int,
        executionCompletionWaiter: (ExecutionCredential_T) -> Unit
    ) {
        val queue = ArrayBlockingQueue<ExecutionCredential_T>(concurrency)

        val executor = ThreadPoolTaskExecutor()
        with(executor) {
            corePoolSize = concurrency
            maxPoolSize = concurrency
            queueCapacity = concurrency
            setWaitForTasksToCompleteOnShutdown(true)
            setAwaitTerminationSeconds(10)
            initialize()
        }

        val allExecutionsSubmitted = AtomicBoolean(false)
        val task = Runnable {
            while (true) {
                try {
                    val allExecutionsSubmittedBefore = allExecutionsSubmitted.get()
                    val execution = queue.poll(1, TimeUnit.SECONDS)
                    if (execution != null) {
                        executionCompletionWaiter(execution)
                    } else {
                        if (allExecutionsSubmittedBefore) {
                            break
                        }
                    }
                } catch (e: Throwable) {
                    log.warn(e) { "Execution completion wait task problem" }
                }
            }
            log.info { "All executions processed" }
        }
        (1..concurrency).forEach { _ ->
            executor.submit(task)
        }

        try {
            var executionCount = 0
            generateSequence {
                if (executionCount < executionLimit) {
                    executionCount++
                    executionStarter()
                } else {
                    null
                }
            }.forEach { credential ->
                val offered = queue.offer(credential, 10, TimeUnit.SECONDS)
                if (offered)
                    log.trace { "Queue unlocked, credential=$credential" }
                else {
                    throw ProcessingException("Enqueue timeout")
                }
            }
        } finally {
            allExecutionsSubmitted.set(true)
            executor.shutdown()
        }
    }

    private fun nextRandomPrice() =
        BigDecimal(Random.nextLong((MAX_ITEM_PRICE.multiply(CURRENCY_MINIMAL_UNITS)).longValueExact()))
            .divide(CURRENCY_MINIMAL_UNITS)

    companion object {
        val MAX_ITEM_PRICE = BigDecimal("50")
        val CURRENCY_MINIMAL_UNITS = BigDecimal("100")

        private val log = KotlinLogging.logger {}
    }
}
