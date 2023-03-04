package ru.dmkuranov.temporaltests.santity

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.dmkuranov.temporaltests.AbstractIntegrationTest
import ru.dmkuranov.temporaltests.core.customerorder.dto.CustomerOrderCreateRequestDto
import ru.dmkuranov.temporaltests.core.customerorder.dto.CustomerOrderItemCreateRequestDto

class ProcessingTrivial : AbstractIntegrationTest() {

    @Test
    fun processTest() {
        val product1 = createProduct(10)
        val product2 = createProduct(10)
        val orderCreateRequest = CustomerOrderCreateRequestDto(
            items = listOf(
                CustomerOrderItemCreateRequestDto(product = product1, quantity = 5),
                CustomerOrderItemCreateRequestDto(product = product2, quantity = 15)
            ), paymentCredential = "card-${System.currentTimeMillis()}"
        )
        val createdOrderId = customerOrderService.createOrder(orderCreateRequest)
        val createdOrder = customerOrderService.loadOrder(createdOrderId)
        assertThat(createdOrder.items).hasSize(2)
        val missingProducts = fulfillmentService.reserveItems(createdOrderId)
        assertThat(missingProducts).containsExactly(product2)
        assertThat(stockService.getStock(product1).quantityReserved).isEqualTo(5L)
        assertThat(stockService.getStock(product2).quantityReserved).isEqualTo(10L)
    }
}
