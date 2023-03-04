package ru.dmkuranov.temporaltests

import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import ru.dmkuranov.temporaltests.core.charge.ChargeService
import ru.dmkuranov.temporaltests.core.customerorder.CustomerOrderService
import ru.dmkuranov.temporaltests.core.processing.FulfillmentService
import ru.dmkuranov.temporaltests.core.stock.StockService
import ru.dmkuranov.temporaltests.core.stock.dto.ProductDto
import ru.dmkuranov.temporaltests.core.stock.dto.StockUpdateRequestDto
import java.math.BigDecimal
import kotlin.random.Random

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

    @Autowired
    protected lateinit var chargeService: ChargeService

    fun createProduct(quantityStock: Long): ProductDto {
        val product = stockService.createProduct()
        val stock = stockService.getStock(product)
        stockService.updateStock(
            StockUpdateRequestDto(stock).copy(
                quantityStock = quantityStock,
                available = (
                    if (quantityStock > 0) true
                    else if (quantityStock == 0L) false
                    else throw IllegalArgumentException("Negative stock quantity: $quantityStock")
                    ),
                price = nextRandomPrice()
            )
        )
        return product
    }

    private fun nextRandomPrice() =
        BigDecimal(Random.nextLong((MAX_ITEM_PRICE.multiply(CURRENCY_MINIMAL_UNITS)).longValueExact()))
            .divide(CURRENCY_MINIMAL_UNITS)

    companion object {
        val MAX_ITEM_PRICE = BigDecimal("50")
        val CURRENCY_MINIMAL_UNITS = BigDecimal("100")
    }
}
