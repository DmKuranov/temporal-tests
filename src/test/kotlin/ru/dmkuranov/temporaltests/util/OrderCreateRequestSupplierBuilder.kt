package ru.dmkuranov.temporaltests.util

import org.springframework.stereotype.Component
import ru.dmkuranov.temporaltests.util.invocation.Invoker
import ru.dmkuranov.temporaltests.util.supplier.OrderCreateRequestSupplier
import ru.dmkuranov.temporaltests.core.stock.StockService
import ru.dmkuranov.temporaltests.core.stock.dto.ProductDto

@Component
class OrderCreateRequestSupplierBuilder(
    private val stockService: StockService
) {

    fun builder(products: List<ProductDto>) =
        Builder(stockService = stockService, products = products)

    data class Builder(
        private val stockService: StockService,
        val products: List<ProductDto>,
        val maxItemsCount: Int = 4,
        val maxItemQuantity: Int = 5,
        val retryNoAvailableProducts: Boolean = false
    ) {
        fun build(): OrderCreateRequestSupplier {
            val invoker = if (retryNoAvailableProducts) whileListNotEmptyInvokerProductDto else Invoker.PlainInvoker()
            return OrderCreateRequestSupplier(
                products = products,
                maxItemQuantity = maxItemQuantity,
                maxItemsCount = maxItemsCount,
                availabilityFilter = { invoker.invoke { stockService.getProductsAvailable(it) } }
            )
        }

    }

    companion object {
        private val whileListNotEmptyInvokerProductDto = Invoker.WhileListNotEmptyInvoker<ProductDto>(
            initialIntervalMs = 100,
            backoffCoefficient = 1.3,
            maxIntervalMs = 5000
        )
    }
}
