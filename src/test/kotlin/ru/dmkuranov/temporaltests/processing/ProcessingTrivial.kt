package ru.dmkuranov.temporaltests.processing

import mu.KotlinLogging
import org.assertj.core.api.Assertions
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
import ru.dmkuranov.temporaltests.core.stock.dto.StockDto
import ru.dmkuranov.temporaltests.core.stock.dto.StockUpdateRequestDto
import java.util.concurrent.Callable
import java.util.concurrent.atomic.AtomicBoolean

class ProcessingTrivial : AbstractIntegrationTest() {

    @Test
    @Order(1)
    fun singleOrderProcessDetailTest() {
        val product1 = createProduct(10)
        val product2 = createProduct(10)
        val snapshot = snapshotService.createSnapshot()
        val orderCreateRequest = CustomerOrderCreateRequestDto(
            items = listOf(
                CustomerOrderItemCreateRequestDto(product = product1, quantity = 4),
                CustomerOrderItemCreateRequestDto(product = product2, quantity = 15)
            ), paymentCredential = "card-${System.currentTimeMillis()}"
        )
        val orderId = customerOrderService.createOrder(orderCreateRequest)
        val createdOrder = customerOrderService.loadOrder(orderId)
        Assertions.assertThat(createdOrder.items).hasSize(2)

        val valid = fulfillmentService.reserveItems(orderId)
        Assertions.assertThat(valid).isTrue
        Assertions.assertThat(stockService.getStock(product1))
            .returns(4, Assertions.from(StockDto::quantityReserved))
            .returns(true, Assertions.from(StockDto::available))
        Assertions.assertThat(stockService.getStock(product2))
            .returns(10, Assertions.from(StockDto::quantityReserved))
            .returns(false, Assertions.from(StockDto::available))

        val charge = chargeService.performChargeOnReserve(orderId)
        val chargeEntities = customerChargeRepository.findByOrderId(orderId)
        Assertions.assertThat(chargeEntities).hasSize(1)
        val chargeEntity = chargeEntities[0]
        Assertions.assertThat(charge.amount).isEqualByComparingTo(chargeEntity.amount)
        Assertions.assertThat(charge.orderId).isEqualTo(orderId)

        stockService.updateStock(
            StockUpdateRequestDto(stockService.getStock(product2)).copy(quantityAvailable = 9)
        )

        val chargeback = fulfillmentService.shipItems(orderId)
        Assertions.assertThat(chargeback).isNotNull
        Assertions.assertThat(stockService.getStock(product1))
            .returns(0, Assertions.from(StockDto::quantityReserved))
            .returns(true, Assertions.from(StockDto::available))
            .returns(4, Assertions.from(StockDto::quantityShipped))
            .returns(6, Assertions.from(StockDto::quantityAvailable))
        Assertions.assertThat(stockService.getStock(product2))
            .returns(0, Assertions.from(StockDto::quantityReserved))
            .returns(false, Assertions.from(StockDto::available))
            .returns(9, Assertions.from(StockDto::quantityShipped))
            .returns(0, Assertions.from(StockDto::quantityAvailable))

        chargeService.performCharge(chargeback!!)
        customerOrderService.completeOrder(orderId)

        val snapshotActual = snapshotService.createSnapshot()
        val snapshotDelta = snapshotActual.delta(snapshot)
        assertThat(snapshotDelta)
            .ordersProcessed(1)
            .itemsQuantityShippedTotal(13)
            .chargebacksPresent()
            .balanceMatch()
    }

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
