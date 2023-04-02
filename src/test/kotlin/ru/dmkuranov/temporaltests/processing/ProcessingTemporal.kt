package ru.dmkuranov.temporaltests.processing

import io.temporal.api.common.v1.WorkflowExecution
import io.temporal.client.WorkflowClient
import io.temporal.client.WorkflowOptions
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import org.mockito.Mockito
import ru.dmkuranov.temporaltests.AbstractTemporalTest
import ru.dmkuranov.temporaltests.core.customerorder.dto.CustomerOrderCreateRequestDto
import ru.dmkuranov.temporaltests.core.customerorder.dto.CustomerOrderItemCreateRequestDto
import ru.dmkuranov.temporaltests.core.stock.dto.ProductDto
import ru.dmkuranov.temporaltests.core.stock.dto.StockUpdateRequestDto
import ru.dmkuranov.temporaltests.temporal.TemporalConfiguration
import ru.dmkuranov.temporaltests.temporal.TemporalNonRetryableException
import ru.dmkuranov.temporaltests.temporal.workflow.order.CustomerOrderWorkflow
import ru.dmkuranov.temporaltests.temporal.workflow.order.CustomerOrderWorkflowResultDto
import ru.dmkuranov.temporaltests.temporal.workflow.supplemental.ResupplyPlannerWorkflow
import ru.dmkuranov.temporaltests.temporal.workflow.supplemental.ShutdownableWorkflow
import ru.dmkuranov.temporaltests.temporal.workflow.supplemental.StealerWorkflow
import ru.dmkuranov.temporaltests.util.asserts.SnapshotStateDeltaAssert
import ru.dmkuranov.temporaltests.util.supplier.OrderCreateRequestSupplier
import java.math.BigDecimal
import java.util.Optional
import java.util.concurrent.TimeUnit

class ProcessingTemporal : AbstractTemporalTest() {

    @Test
    @Order(1)
    fun singleOrderProcessTest(testInfo: TestInfo) {
        val product1 = createProduct(10)
        val product2 = createProduct(10)
        val snapshot = snapshotService.createSnapshot()

        val workflowId = generateWorkflowId(testInfo)
        val workflow = workflowClient.newWorkflowStub(
            CustomerOrderWorkflow::class.java,
            WorkflowOptions.getDefaultInstance()
                .toBuilder()
                .setTaskQueue(TemporalConfiguration.TASK_QUEUE_NAME)
                .setWorkflowId(workflowId)
                .build()
        )

        val orderCreateRequest = CustomerOrderCreateRequestDto(
            items = listOf(
                CustomerOrderItemCreateRequestDto(product = product1, quantity = 4),
                CustomerOrderItemCreateRequestDto(product = product2, quantity = 15)
            ), paymentCredential = "card-${System.currentTimeMillis()}"
        )

        WorkflowClient.start(workflow::process, orderCreateRequest)!!

        val wfStub = workflowClient.newUntypedWorkflowStub(workflowId)
        val wfResult = wfStub.getResult(CustomerOrderWorkflowResultDto::class.java)
        Assertions.assertThat(wfResult.valid).isTrue

        val snapshotActual = snapshotService.createSnapshot()
        val snapshotDelta = snapshotActual.delta(snapshot)
        SnapshotStateDeltaAssert.assertThat(snapshotDelta)
            .ordersProcessed(1)
            .itemsQuantityShippedTotal(14)
            .chargebacksNotPresent()
            .balanceMatchStrict()
    }

    @Test
    @Order(1)
    fun fullCompensationTest(testInfo: TestInfo) {
        val initialQuantity = 10L
        val availableQuantityAfterStealing = 3L

        val product1 = createProduct(initialQuantity)
        val product2 = createProduct(initialQuantity)
        assertStockQuantitiesUntouched(listOf(product1 to initialQuantity, product2 to initialQuantity))

        Mockito.doAnswer { invocation ->
            val result = invocation.callRealMethod()
            val stock = stockService.getStock(product1)
            stockService.updateStock(StockUpdateRequestDto(stock).copy(quantityAvailable = availableQuantityAfterStealing))
            result
        }.`when`(fulfillmentService).reserveItems(Mockito.anyLong())

        Mockito.doAnswer { _ ->
            Mockito.reset(customerOrderService)
            throw TemporalNonRetryableException()
        }.`when`(customerOrderService).completeOrder(Mockito.anyLong())

        val snapshot = snapshotService.createSnapshot()

        val workflowId = generateWorkflowId(testInfo)
        val workflow = workflowClient.newWorkflowStub(
            CustomerOrderWorkflow::class.java,
            WorkflowOptions.getDefaultInstance()
                .toBuilder()
                .setTaskQueue(TemporalConfiguration.TASK_QUEUE_NAME)
                .setWorkflowId(workflowId)
                .build()
        )

        val orderCreateRequest = CustomerOrderCreateRequestDto(
            items = listOf(
                CustomerOrderItemCreateRequestDto(product = product1, quantity = 4),
                CustomerOrderItemCreateRequestDto(product = product2, quantity = 15)
            ), paymentCredential = "card-${System.currentTimeMillis()}"
        )

        WorkflowClient.start(workflow::process, orderCreateRequest)!!

        val wfStub = workflowClient.newUntypedWorkflowStub(workflowId)
        val wfResult = wfStub.getResult(CustomerOrderWorkflowResultDto::class.java)
        Assertions.assertThat(wfResult.failed).isTrue

        assertStockQuantitiesUntouched(listOf(product1 to availableQuantityAfterStealing, product2 to initialQuantity))
        val charges = chargeService.getCharges(wfResult.orderId)
        Assertions.assertThat(charges).hasSize(3)
        Assertions.assertThat(charges.sumOf { it.amount }).isEqualByComparingTo(BigDecimal.ZERO)

        val snapshotActual = snapshotService.createSnapshot()
        val snapshotDelta = snapshotActual.delta(snapshot)
        SnapshotStateDeltaAssert.assertThat(snapshotDelta)
            .ordersProcessed(1)
            .itemsQuantityShippedTotal(0)
            .chargebacksPresent()
            .balanceMatch()
    }

    @Test
    @Order(2)
    fun multipleOrderSequentialProcessTest(testInfo: TestInfo) {
        val productCount = 10
        val ordersToProcess = 10
        val orderCreateRequestSupplier = orderCreateRequestSupplierBuilder.builder(createProducts(count = productCount))
            .copy(maxItemQuantity = 25).build()

        val snapshot = snapshotService.createSnapshot()

        (1..ordersToProcess).forEach { _ ->
            val workflowId = generateWorkflowId(testInfo)
            val workflow = workflowClient.newWorkflowStub(
                CustomerOrderWorkflow::class.java,
                WorkflowOptions.getDefaultInstance()
                    .toBuilder()
                    .setTaskQueue(TemporalConfiguration.TASK_QUEUE_NAME)
                    .setWorkflowId(workflowId)
                    .build()
            )

            val orderCreateRequest = orderCreateRequestSupplier.next()

            WorkflowClient.start(workflow::process, orderCreateRequest)!!

            val wfStub = workflowClient.newUntypedWorkflowStub(workflowId)
            val wfResult = wfStub.getResult(CustomerOrderWorkflowResultDto::class.java)
            Assertions.assertThat(wfResult.valid).isTrue
        }

        val snapshotActual = snapshotService.createSnapshot()
        val snapshotDelta = snapshotActual.delta(snapshot)
        SnapshotStateDeltaAssert.assertThat(snapshotDelta)
            .ordersProcessed(ordersToProcess)
            .itemsQuantityShippedTotal(orderCreateRequestSupplier)
            .chargebacksNotPresent()
            .balanceMatchStrict()
    }

    data class TemporalExecution(
        val workflowId: String
    )

    @Test
    @Order(3)
    fun multipleOrderParallelProcessTest(testInfo: TestInfo) {
        val productCount = 10
        val ordersToProcess = 300
        val orderCreateRequestSupplier = orderCreateRequestSupplierBuilder
            .builder(createProducts(count = productCount, initialStockQuantity = 10000000))
            .copy(maxItemQuantity = 25).build()

        val snapshot = snapshotService.createSnapshot()

        executeProcessing(
            testInfo = testInfo,
            orderCreateRequestSupplier = orderCreateRequestSupplier,
            ordersToProcess = ordersToProcess
        )

        val snapshotActual = snapshotService.createSnapshot()
        val snapshotDelta = snapshotActual.delta(snapshot)
        SnapshotStateDeltaAssert.assertThat(snapshotDelta)
            .ordersProcessed(ordersToProcess)
            .itemsQuantityShippedTotal(orderCreateRequestSupplier)
            .chargebacksNotPresent()
            .balanceMatchStrict()
    }

    @Test
    @Order(4)
    fun multipleOrderParallelProcessWithResupplyTest(testInfo: TestInfo) {
        val productCount = 10
        val productInitialStock = 100L
        val ordersToProcess = 300

        val products = createProducts(count = productCount, initialStockQuantity = productInitialStock)
        val orderCreateRequestSupplier = orderCreateRequestSupplierBuilder.builder(products)
            .copy(maxItemQuantity = 25, retryNoAvailableProducts = true).build()

        val snapshot = snapshotService.createSnapshot()

        executeProcessing(
            testInfo = testInfo,
            orderCreateRequestSupplier = orderCreateRequestSupplier,
            ordersToProcess = ordersToProcess,
            resupplyEnabled = true
        )

        val snapshotActual = snapshotService.createSnapshot()
        val snapshotDelta = snapshotActual.delta(snapshot)
        SnapshotStateDeltaAssert.assertThat(snapshotDelta)
            .ordersProcessed(ordersToProcess)
            .itemsQuantityShippedTotalAdequate(
                orderCreateRequestSupplier = orderCreateRequestSupplier,
                shippingToAverageOrderedRatio = shippingToAverageOrderedRatioFull - shippingToAverageOrderedRatioResupplyAffect -
                    shippingToAverageOrderedRatioStealingAffect - shippingToAverageOrderedRatioTolerance
            )
            .balanceMatch()
    }

    @Test
    @Order(5)
    fun multipleOrderParallelProcessWithResupplyAndStealTest(testInfo: TestInfo) {
        val productCount = 10
        val productInitialStock = 100L
        val ordersToProcess = 300

        val products = createProducts(count = productCount, initialStockQuantity = productInitialStock)
        val orderCreateRequestSupplier = orderCreateRequestSupplierBuilder.builder(products)
            .copy(maxItemQuantity = 25, retryNoAvailableProducts = true).build()

        val snapshot = snapshotService.createSnapshot()

        executeProcessing(
            testInfo = testInfo,
            orderCreateRequestSupplier = orderCreateRequestSupplier,
            ordersToProcess = ordersToProcess,
            resupplyEnabled = true,
            stealEnabled = true
        )

        val snapshotActual = snapshotService.createSnapshot()
        val snapshotDelta = snapshotActual.delta(snapshot)
        SnapshotStateDeltaAssert.assertThat(snapshotDelta)
            .ordersProcessed(ordersToProcess)
            .itemsQuantityShippedTotalAdequate(
                orderCreateRequestSupplier = orderCreateRequestSupplier,
                shippingToAverageOrderedRatio = shippingToAverageOrderedRatioFull - shippingToAverageOrderedRatioResupplyAffect -
                    shippingToAverageOrderedRatioStealingAffect - shippingToAverageOrderedRatioTolerance
            )
            .chargebacksPresent()
            .balanceMatch()
    }

    @Test
    @Order(6)
    fun multipleOrderParallelProcessWithFailureCompensationTest(testInfo: TestInfo) {
        val failureRatio = 0.30
        mockServiceFailures(totalFailureRatio = failureRatio)

        val productCount = 10
        val ordersToProcess = 300
        val orderCreateRequestSupplier = orderCreateRequestSupplierBuilder
            .builder(createProducts(count = productCount, initialStockQuantity = 10000000))
            .copy(maxItemQuantity = 25).build()

        val snapshot = snapshotService.createSnapshot()

        executeProcessing(
            testInfo = testInfo,
            orderCreateRequestSupplier = orderCreateRequestSupplier,
            ordersToProcess = ordersToProcess
        )

        val snapshotActual = snapshotService.createSnapshot()
        val snapshotDelta = snapshotActual.delta(snapshot)
        SnapshotStateDeltaAssert.assertThat(snapshotDelta)
            .ordersProcessed(ordersToProcess)
            .balanceMatchStrict()
            .itemsQuantityShippedTotalAdequate(
                orderCreateRequestSupplier = orderCreateRequestSupplier,
                shippingToAverageOrderedRatio = shippingToAverageOrderedRatioFull - failureRatio -
                    shippingToAverageOrderedRatioTolerance
            )
    }

    @Test
    @Order(7)
    fun multipleOrderParallelProcessWithResupplyAndStealWithFailureCompensationTest(testInfo: TestInfo) {
        val failureRatio = 0.30
        mockServiceFailures(totalFailureRatio = failureRatio)

        val productCount = 10
        val productInitialStock = 100L
        val ordersToProcess = 1000

        val products = createProducts(count = productCount, initialStockQuantity = productInitialStock)
        val orderCreateRequestSupplier = orderCreateRequestSupplierBuilder.builder(products)
            .copy(maxItemQuantity = 25, retryNoAvailableProducts = true).build()

        val snapshot = snapshotService.createSnapshot()

        executeProcessing(
            testInfo = testInfo,
            orderCreateRequestSupplier = orderCreateRequestSupplier,
            ordersToProcess = ordersToProcess,
            resupplyEnabled = true,
            stealEnabled = true
        )

        val snapshotActual = snapshotService.createSnapshot()
        val snapshotDelta = snapshotActual.delta(snapshot)
        SnapshotStateDeltaAssert.assertThat(snapshotDelta)
            .ordersProcessed(ordersToProcess)
            .balanceMatch()
            .itemsQuantityShippedTotalAdequate(
                orderCreateRequestSupplier = orderCreateRequestSupplier,
                shippingToAverageOrderedRatio = shippingToAverageOrderedRatioFull - failureRatio -
                    shippingToAverageOrderedRatioResupplyAffect - shippingToAverageOrderedRatioStealingAffect - shippingToAverageOrderedRatioTolerance
            )
            .chargebacksPresent()
    }

    private fun executeProcessing(
        testInfo: TestInfo,
        orderCreateRequestSupplier: OrderCreateRequestSupplier,
        ordersToProcess: Int,
        resupplyEnabled: Boolean = false,
        stealEnabled: Boolean = false
    ) {
        val products = orderCreateRequestSupplier.products
        val shutdownableWorkflows = mutableListOf<Pair<ShutdownableWorkflow, WorkflowExecution>>()
        if (stealEnabled) {
            shutdownableWorkflows.add(
                startSteal(products = products)
            )
        }
        if (resupplyEnabled) {
            shutdownableWorkflows.add(
                startResupply(
                    products = products,
                    stealerEnabled = stealEnabled
                )
            )
        }

        try {
            val concurrency = 5
            executeWorkflow(concurrency = concurrency,
                executionStarter = {
                    val workflowId = generateWorkflowId(testInfo)
                    val workflow = workflowClient.newWorkflowStub(
                        CustomerOrderWorkflow::class.java,
                        WorkflowOptions.getDefaultInstance()
                            .toBuilder()
                            .setTaskQueue(TemporalConfiguration.TASK_QUEUE_NAME)
                            .setWorkflowId(workflowId)
                            .build()
                    )
                    val orderCreateRequest = orderCreateRequestSupplier.next()
                    WorkflowClient.start(workflow::process, orderCreateRequest)!!
                    TemporalExecution(workflowId = workflowId)
                },
                executionLimit = ordersToProcess,
                executionCompletionWaiter = { credential ->
                    val wfStub = workflowClient.newUntypedWorkflowStub(credential.workflowId)
                    wfStub.getResult(CustomerOrderWorkflowResultDto::class.java)
                }
            )
        } finally {
            shutdownableWorkflows.forEach { it.first.shutdown() }
            shutdownableWorkflows.forEach {
                workflowClient.newUntypedWorkflowStub(it.second, Optional.empty())
                    .getResult(5, TimeUnit.SECONDS, Unit::class.java)
            }
        }
    }

    private fun startResupply(products: List<ProductDto>, stealerEnabled: Boolean = false) =
        workflowClient.newWorkflowStub(
            ResupplyPlannerWorkflow::class.java,
            WorkflowOptions.getDefaultInstance()
                .toBuilder()
                .setTaskQueue(TemporalConfiguration.TASK_QUEUE_NAME)
                .setWorkflowId(ResupplyPlannerWorkflow.WORKFLOW_NAME)
                .build()
        )!!
            .let { it to WorkflowClient.start(it::planResupply, products, stealerEnabled) }

    private fun startSteal(products: List<ProductDto>) =
        workflowClient.newWorkflowStub(
            StealerWorkflow::class.java,
            WorkflowOptions.getDefaultInstance()
                .toBuilder()
                .setTaskQueue(TemporalConfiguration.TASK_QUEUE_NAME)
                .setWorkflowId(StealerWorkflow.WORKFLOW_NAME)
                .build()
        )!!
            .let { it to WorkflowClient.start(it::startSteal, products) }

    private fun assertStockQuantitiesUntouched(productToQuantities: List<Pair<ProductDto, Long>>) {
        productToQuantities.forEach { productToQuantity ->
            val stock = stockService.getStock(productToQuantity.first)
            Assertions.assertThat(stock.quantityAvailable).isEqualTo(productToQuantity.second)
            Assertions.assertThat(stock.quantityReserved).isEqualTo(0L)
            Assertions.assertThat(stock.quantityShipped).isEqualTo(0L)
        }
    }

    companion object {
        private const val shippingToAverageOrderedRatioFull = 1.0
        private const val shippingToAverageOrderedRatioResupplyAffect = 0.1
        private const val shippingToAverageOrderedRatioTolerance = 0.1
        private const val shippingToAverageOrderedRatioStealingAffect = 0.1
    }
}
