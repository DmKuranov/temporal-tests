package ru.dmkuranov.temporaltests.santity

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.dmkuranov.temporaltests.AbstractIntegrationTest
import ru.dmkuranov.temporaltests.core.stock.dto.StockUpdateRequestDto

class SampleEntityPersistenceTest : AbstractIntegrationTest() {

    @Test
    fun createProduct() {
        stockService.createProduct()
    }

    @Test
    fun updateProduct() {
        val quantityTarget = 10L
        val product = stockService.createProduct()
        val stock = stockService.getStock(product)
        assertThat(stock.quantityAvailable).isNotEqualTo(quantityTarget)

        val updateRequest = StockUpdateRequestDto(stock)
            .copy(available = true, quantityAvailable = 10)
        val stockUpdated = stockService.updateStock(updateRequest)
        assertThat(stockUpdated.quantityAvailable).isEqualTo(quantityTarget)
    }
}
