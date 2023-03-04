package ru.dmkuranov.temporaltests

import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import ru.dmkuranov.temporaltests.core.customerorder.CustomerOrderService
import ru.dmkuranov.temporaltests.core.processing.FulfillmentService
import ru.dmkuranov.temporaltests.core.stock.StockService
import ru.dmkuranov.temporaltests.core.stock.dto.ProductDto
import ru.dmkuranov.temporaltests.core.stock.dto.StockUpdateRequestDto

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = ["spring.main.allow-bean-definition-overriding = true"])
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Suppress("UnnecessaryAbstractClass") // abstract members not required
abstract class AbstractIntegrationTest {
    @Autowired
    protected lateinit var stockService: StockService

    @Autowired
    protected lateinit var customerOrderService: CustomerOrderService

    @Autowired
    protected lateinit var fulfillmentService: FulfillmentService

    fun createProduct(quantityStock: Long): ProductDto {
        val product = stockService.createProduct()
        val stock = stockService.getStock(product)
        stockService.updateStock(
            StockUpdateRequestDto(stock).copy(
                quantityStock = quantityStock,
                available = (if (quantityStock > 0) true
                else if (quantityStock == 0L) false
                else throw IllegalArgumentException("Negative stock quantity: $quantityStock"))
            )
        )
        return product
    }
}
