package ru.dmkuranov.temporaltests.santity

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import ru.dmkuranov.temporaltests.AbstractIntegrationTest
import ru.dmkuranov.temporaltests.core.customerorder.dto.CustomerOrderCreateRequestDto
import ru.dmkuranov.temporaltests.core.customerorder.dto.CustomerOrderItemCreateRequestDto
import ru.dmkuranov.temporaltests.core.stock.dto.StockDto
import ru.dmkuranov.temporaltests.core.stock.dto.StockUpdateRequestDto
import ru.dmkuranov.temporaltests.util.asserts.SnapshotStateDeltaAssert

class TrivialCoreSmokeTest : AbstractIntegrationTest() {

    @Test
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
        SnapshotStateDeltaAssert.assertThat(snapshotDelta)
            .ordersProcessed(1)
            .itemsQuantityShippedTotal(13)
            .chargebacksPresent()
            .balanceMatch()
    }
}
