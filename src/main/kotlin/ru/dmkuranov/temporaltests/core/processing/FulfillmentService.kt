package ru.dmkuranov.temporaltests.core.processing

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.dmkuranov.temporaltests.core.customerorder.CustomerOrderService
import ru.dmkuranov.temporaltests.core.customerorder.dto.CustomerOrderItemDto
import ru.dmkuranov.temporaltests.core.stock.StockService
import ru.dmkuranov.temporaltests.core.stock.dto.ProductDto
import ru.dmkuranov.temporaltests.core.stock.dto.StockUpdateRequestDto

@Service
class FulfillmentService(
    private val stockService: StockService,
    private val customerOrderService: CustomerOrderService
) {

    /**
     * @return missing items
     */
    @Transactional
    fun reserveItems(orderId: Long): List<ProductDto> =
        customerOrderService.loadOrder(orderId).let {
            val missingItems = mutableListOf<ProductDto>()
            it.items.forEach { reserveOrderItem(it, missingItems) }
            missingItems
        }

    private fun reserveOrderItem(item: CustomerOrderItemDto, missingItems: MutableCollection<ProductDto>) {
        val itemStock = stockService.getStock(item.product)
        val availableForReserve = with(itemStock) { if (quantityStock > quantityReserved) quantityStock - quantityReserved else 0 }
        val quantityToReserve = if (availableForReserve > item.quantityOrdered) item.quantityOrdered else availableForReserve
        if (quantityToReserve < item.quantityOrdered) missingItems.add(item.product)

        stockService.updateStock(
            StockUpdateRequestDto(itemStock)
            .let { updateRequest -> updateRequest.copy(quantityReserved = updateRequest.quantityReserved + quantityToReserve) })
        customerOrderService.adjustOrderItem(item.itemId, quantityReserved = quantityToReserve)
    }
}
