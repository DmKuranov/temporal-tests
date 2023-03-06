package ru.dmkuranov.temporaltests.processing

import mu.KotlinLogging
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import ru.dmkuranov.temporaltests.util.asserts.SnapshotStateDeltaAssert.Companion.assertThat
import ru.dmkuranov.temporaltests.util.invocation.Invoker
import ru.dmkuranov.temporaltests.util.processing.CloseableExecutor
import ru.dmkuranov.temporaltests.util.processing.ResupplyTrivialTask
import ru.dmkuranov.temporaltests.util.processing.StealTask
import ru.dmkuranov.temporaltests.util.supplier.OrderCreateRequestSupplier
import ru.dmkuranov.temporaltests.AbstractIntegrationTest
import ru.dmkuranov.temporaltests.core.customerorder.dto.CustomerOrderCreateRequestDto
import ru.dmkuranov.temporaltests.core.customerorder.dto.CustomerOrderItemCreateRequestDto
import java.util.concurrent.Callable
import java.util.concurrent.atomic.AtomicBoolean

class ProcessingTrivial : AbstractIntegrationTest() {

    @Test
    @Order(1)
    fun singleOrderProcessTest() {
        val product1 = createProduct(10)
        val product2 = createProduct(10)
        val snapshot = snapshotService.createSnapshot()
        val orderCreateRequest = CustomerOrderCreateRequestDto(
            items = listOf(
                CustomerOrderItemCreateRequestDto(product = product1, quantity = 4),
                CustomerOrderItemCreateRequestDto(product = product2, quantity = 15)
            ), paymentCredential = "card-${System.currentTimeMillis()}"
        )

        processTrivialOrder(orderCreateRequest)

        val snapshotActual = snapshotService.createSnapshot()
        val snapshotDelta = snapshotActual.delta(snapshot)
        assertThat(snapshotDelta)
            .ordersProcessed(1)
            .itemsQuantityShippedTotal(14)
            .chargebacksNotPresent()
            .balanceMatchStrict()
    }

    @Test
    @Order(2)
    fun multipleOrderSequentialProcessTest() {
        val productCount = 10
        val ordersToProcess = 50
        val orderCreateRequestSupplier = orderCreateRequestSupplierBuilder.builder(createProducts(count = productCount))
            .copy(maxItemQuantity = 25).build()

        val snapshot = snapshotService.createSnapshot()

        var count = 0
        generateSequence {
            if (count < ordersToProcess) {
                count++
                orderCreateRequestSupplier.next()
            } else
                null
        }.forEach { processTrivialOrder(it) }

        val snapshotActual = snapshotService.createSnapshot()
        val snapshotDelta = snapshotActual.delta(snapshot)
        assertThat(snapshotDelta)
            .ordersProcessed(ordersToProcess)
            .itemsQuantityShippedTotal(orderCreateRequestSupplier)
            .chargebacksNotPresent()
            .balanceMatchStrict()
    }

    @Test
    @Order(3)
    fun multipleOrderParallelProcessTest() {
        val productCount = 10
        val ordersToProcess = 300
        val orderCreateRequestSupplier = orderCreateRequestSupplierBuilder.builder(createProducts(count = productCount))
            .copy(maxItemQuantity = 25).build()

        val snapshot = snapshotService.createSnapshot()

        executeConcurrentProcessing(
            orderCreateRequestSupplier = orderCreateRequestSupplier,
            executionLimit = ordersToProcess
        )

        val snapshotActual = snapshotService.createSnapshot()
        val snapshotDelta = snapshotActual.delta(snapshot)
        assertThat(snapshotDelta)
            .itemsQuantityShippedTotal(orderCreateRequestSupplier)
            .chargebacksNotPresent()
            .balanceMatchStrict()
    }

    @Test
    @Order(4)
    fun multipleOrderParallelProcessWithResupplyTest() {
        val productCount = 10
        val productInitialStock = 100L
        val ordersToProcess = 1000

        val products = createProducts(count = productCount, initialStockQuantity = productInitialStock)
        val orderCreateRequestSupplier = orderCreateRequestSupplierBuilder.builder(products)
            .copy(maxItemQuantity = 25, retryNoAvailableProducts = true).build()

        val snapshot = snapshotService.createSnapshot()

        executeConcurrentProcessing(
            orderCreateRequestSupplier = orderCreateRequestSupplier,
            executionLimit = ordersToProcess,
            enableResupply = true
        )

        val snapshotActual = snapshotService.createSnapshot()
        val snapshotDelta = snapshotActual.delta(snapshot)
        assertThat(snapshotDelta)
            .ordersProcessed(ordersToProcess)
            .itemsQuantityShippedTotalAdequate(
                orderCreateRequestSupplier = orderCreateRequestSupplier,
                shippingToAverageOrderedRatio = shippingToAverageOrderedRatioFull -
                    shippingToAverageOrderedRatioResupplyAffect - shippingToAverageOrderedRatioTolerance
            )
            .chargebacksNotPresent()
            .balanceMatch()
    }

    @Test
    @Order(5)
    fun multipleOrderParallelProcessWithResupplyAndStealTest() {
        val productCount = 10
        val productInitialStock = 100L
        val ordersToProcess = 1000

        val products = createProducts(count = productCount, initialStockQuantity = productInitialStock)
        val orderCreateRequestSupplier = orderCreateRequestSupplierBuilder.builder(products)
            .copy(maxItemQuantity = 25, retryNoAvailableProducts = true).build()

        val snapshot = snapshotService.createSnapshot()

        executeConcurrentProcessing(
            orderCreateRequestSupplier = orderCreateRequestSupplier,
            executionLimit = ordersToProcess,
            enableResupply = true,
            enableStealing = true
        )

        val snapshotActual = snapshotService.createSnapshot()
        val snapshotDelta = snapshotActual.delta(snapshot)
        assertThat(snapshotDelta)
            .ordersProcessed(ordersToProcess)
            .itemsQuantityShippedTotalAdequate(
                orderCreateRequestSupplier = orderCreateRequestSupplier,
                shippingToAverageOrderedRatio = shippingToAverageOrderedRatioFull -
                    shippingToAverageOrderedRatioResupplyAffect - shippingToAverageOrderedRatioTolerance -
                    shippingToAverageOrderedRatioStealingAffect
            )
            .chargebacksPresent()
            .balanceMatch()
    }

    fun processTrivialOrder(
        orderCreateRequest: CustomerOrderCreateRequestDto,
        invoker: Invoker<Any?> = Invoker.plain
    ): Boolean {
        return try {
            val orderId = invoker.invoke { customerOrderService.createOrder(orderCreateRequest) }
            val valid = invoker.invoke { fulfillmentService.reserveItems(orderId) }
            if (valid) {
                invoker.invoke { chargeService.performChargeOnReserve(orderId) }
                val chargeback = invoker.invoke { fulfillmentService.shipItems(orderId) }
                if (chargeback != null) {
                    invoker.invoke { chargeService.performCharge(chargeback) }
                }
            } else {
                log.info { "order $orderId invalid" }
            }
            invoker.invoke { customerOrderService.completeOrder(orderId) }
            valid
        } catch (e: Exception) {
            log.info(e) { "Processing exception" }
            false
        }
    }

    /**
     * Производит обработку указанного количества заказов с ограничением параллелизма
     */
    private fun executeConcurrentProcessing(
        orderCreateRequestSupplier: OrderCreateRequestSupplier,
        executionLimit: Int,
        enableResupply: Boolean = false,
        enableStealing: Boolean = false
    ) {
        val concurrency = 5
        (CloseableExecutor()).use { processor ->
            val running = AtomicBoolean(true)
            if (enableResupply) {
                processor.submit(
                    ResupplyTrivialTask(
                        products = orderCreateRequestSupplier.products,
                        running = running,
                        executor = processor,
                        transactionalInvoker = transactionalInvoker,
                        stockService = stockService
                    )
                )
            }
            if (enableStealing) {
                processor.submit(
                    StealTask(
                        orderCreateRequestSupplier = orderCreateRequestSupplier,
                        running = running,
                        transactionalInvoker = transactionalInvoker,
                        stockService = stockService
                    )
                )
            }
            executeWorkflow(
                concurrency = concurrency,
                executionStarter = {
                    val orderCreateRequest = orderCreateRequestSupplier.next()
                    processor.submit(
                        Callable { processTrivialOrder(orderCreateRequest, Invoker.dbConcurrencyRetry) }
                    )
                },
                executionLimit = executionLimit,
                executionCompletionWaiter = {
                    it.get()
                }
            )
            running.set(false)
        }
    }

    companion object {
        private const val shippingToAverageOrderedRatioFull = 1.0
        private const val shippingToAverageOrderedRatioResupplyAffect = 0.03
        private const val shippingToAverageOrderedRatioTolerance = 0.07
        private const val shippingToAverageOrderedRatioStealingAffect = 0.1

        private val log = KotlinLogging.logger {}
    }
}
