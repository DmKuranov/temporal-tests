package ru.dmkuranov.temporaltests.util.dto

import org.assertj.core.api.Assertions.assertThat
import ru.dmkuranov.temporaltests.core.stock.dto.StockDto
import java.math.BigDecimal

data class SnapshotState(
    val stocks: Map<Long, StockDto>,
    val orderQuantities: Map<Long, OrderQuantityAggregate>,
    val chargeAmount: BigDecimal,
    val chargebackAmount: BigDecimal,
    val orderCount: Long
) {
    fun delta(previous: SnapshotState): SnapshotStateDelta {
        assertThat(stocks.keys).containsAll(previous.stocks.keys)
        assertThat(orderQuantities.keys).containsAll(previous.orderQuantities.keys)
        var chargeAmountShipped = BigDecimal.ZERO
        val stockQuantities = stocks.mapValues { entry ->
            val stockActual = entry.value
            val stockPrevious = previous.stocks[entry.key] ?: EMPTY_STOCK
            val quantityShipped = stockActual.quantityShipped - stockPrevious.quantityShipped
            assertThat(quantityShipped).isGreaterThanOrEqualTo(0L)
            assertThat(stockActual.price).isEqualByComparingTo(stockPrevious.price)
            chargeAmountShipped = chargeAmountShipped.plus(stockActual.price.multiply(BigDecimal(quantityShipped)))
            StockQuantityAggregate(
                quantityAvailable = stockActual.quantityAvailable - stockPrevious.quantityAvailable,
                quantityReserved = stockActual.quantityReserved - stockPrevious.quantityReserved,
                quantityShipped = quantityShipped
            )
        }.filterValues { quantity -> quantity.quantityAvailable != 0L || quantity.quantityReserved != 0L || quantity.quantityShipped != 0L }

        val orderQuantities = orderQuantities.mapValues { entry ->
            val orderActual = entry.value
            val orderPrevious = previous.orderQuantities[entry.key] ?: EMPTY_ORDER_AGGREGATE
            orderActual.minus(orderPrevious)
        }.filterValues { quantity -> quantity.quantityOrdered != 0L || quantity.quantityReserved != 0L || quantity.quantityShipped != 0L }

        return SnapshotStateDelta(
            stockQuantities = stockQuantities,
            orderQuantities = orderQuantities,
            chargeAmountShipped = chargeAmountShipped,
            chargeAmountCharged = chargeAmount.minus(previous.chargeAmount),
            chargebackAmountDelta = chargebackAmount.minus(previous.chargebackAmount),
            orderCount = (orderCount - previous.orderCount).toInt()
        )
    }

    companion object {
        val EMPTY_STOCK = StockDto(
            productId = -1,
            quantityAvailable = 0,
            quantityReserved = 0,
            quantityShipped = 0,
            quantitySupplyAwaiting = 0,
            available = false,
            price = BigDecimal.ZERO
        )
        val EMPTY_ORDER_AGGREGATE = OrderQuantityAggregate()
    }
}
