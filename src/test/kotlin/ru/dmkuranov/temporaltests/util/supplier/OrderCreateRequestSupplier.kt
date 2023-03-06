package ru.dmkuranov.temporaltests.util.supplier

import ru.dmkuranov.temporaltests.core.customerorder.dto.CustomerOrderCreateRequestDto
import ru.dmkuranov.temporaltests.core.customerorder.dto.CustomerOrderItemCreateRequestDto
import ru.dmkuranov.temporaltests.core.stock.dto.ProductDto
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

class OrderCreateRequestSupplier(
    val products: List<ProductDto>,
    val maxItemsCount: Int,
    val maxItemQuantity: Int,
    val availabilityFilter: (List<ProductDto>) -> List<ProductDto> = { it }
) {
    private val productSupplier = RandomEntityPoolSupplier(products)
    private val quantityOrdered = AtomicLong(0)

    fun next(): CustomerOrderCreateRequestDto {
        val items = nextAvailableProducts(maxItemsCount)
            .map { product ->
                val quantity = Random.nextInt(1, maxItemQuantity).toLong()
                quantityOrdered.addAndGet(quantity)
                CustomerOrderItemCreateRequestDto(
                    product = product,
                    quantity = quantity
                )
            }.toList()
        return CustomerOrderCreateRequestDto(
            items = items, paymentCredential = "card-${System.currentTimeMillis()}"
        )
    }

    fun getQuantityOrderedTotal() =
        quantityOrdered.get()

    private fun nextAvailableProducts(count: Int): List<ProductDto> =
        productSupplier.next(
            if (count == 1) 1 else Random.nextInt(1, count),
            availabilityFilter
        )
}
