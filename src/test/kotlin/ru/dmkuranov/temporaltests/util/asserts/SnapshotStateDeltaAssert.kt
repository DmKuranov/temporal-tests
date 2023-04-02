package ru.dmkuranov.temporaltests.util.asserts

import mu.KotlinLogging
import org.assertj.core.api.AbstractAssert
import org.assertj.core.api.Assertions
import ru.dmkuranov.temporaltests.util.dto.SnapshotStateDelta
import ru.dmkuranov.temporaltests.util.supplier.OrderCreateRequestSupplier
import java.math.BigDecimal

class SnapshotStateDeltaAssert(actual: SnapshotStateDelta, selfType: Class<*>?) :
    AbstractAssert<SnapshotStateDeltaAssert, SnapshotStateDelta>(actual, selfType) {

    fun balanceMatchStrict(): SnapshotStateDeltaAssert {
        balanceMatch()
        with(actual) {
            Assertions.assertThat(stockQuantities.keys).containsAll(orderQuantities.keys)
            Assertions.assertThat(stockQuantities).allSatisfy { _, stockAggregate ->
                Assertions.assertThat(stockAggregate.quantityAvailable).isEqualTo(-stockAggregate.quantityShipped)
            }
        }
        return this
    }

    fun balanceMatch(): SnapshotStateDeltaAssert {
        with(actual) {
            Assertions.assertThat(stockQuantities).allSatisfy { productId, stockAggregate ->
                Assertions.assertThat(stockAggregate.quantityReserved).isEqualTo(0L)
                val orderAggregate = orderQuantities[productId]!!
                Assertions.assertThat(orderAggregate.quantityShipped)
                    .isEqualTo(stockAggregate.quantityShipped)
                    .isLessThanOrEqualTo(orderAggregate.quantityReserved)
                Assertions.assertThat(orderAggregate.quantityReserved)
                    .isLessThanOrEqualTo(orderAggregate.quantityOrdered)
                Assertions.assertThat(chargeAmountShipped).isEqualByComparingTo(chargeAmountCharged)
            }
        }
        return this
    }

    fun ordersProcessed(expected: Int): SnapshotStateDeltaAssert {
        Assertions.assertThat(actual.orderCount).isEqualTo(expected)
        return this
    }

    /**
     * Проверка достаточности отгруженного количества относительно заказанного
     * @param shippingToAverageOrderedRatio доля соответствия(80% по умолчанию)
     */
    fun itemsQuantityShippedTotalAdequate(
        orderCreateRequestSupplier: OrderCreateRequestSupplier,
        shippingToAverageOrderedRatio: Double = 0.9999
    ): SnapshotStateDeltaAssert {
        val quantityOrderedTotal = orderCreateRequestSupplier.getQuantityOrderedTotal()
        Assertions.assertThat(quantityOrderedTotal).isGreaterThanOrEqualTo(0L)
        if (quantityOrderedTotal > 0) {
            val lowerBound = (shippingToAverageOrderedRatio * quantityOrderedTotal).toLong()
            val upperBound = (1.0001 * quantityOrderedTotal).toLong()
            val shippedTotal = actual.stockQuantities.values.sumOf { it.quantityShipped }
            log.info { String.format("Shipped to ordered: %.2f%%", 100.0 * shippedTotal / quantityOrderedTotal) }
            Assertions.assertThat(shippedTotal)
                .isGreaterThan(lowerBound)
                .isLessThanOrEqualTo(upperBound)
            Assertions.assertThat(actual.orderQuantities.values.sumOf { it.quantityShipped })
                .isGreaterThan(lowerBound)
                .isLessThanOrEqualTo(upperBound)
        }
        return this
    }

    fun itemsQuantityShippedTotal(orderCreateRequestSupplier: OrderCreateRequestSupplier) =
        itemsQuantityShippedTotal(orderCreateRequestSupplier.getQuantityOrderedTotal())

    fun itemsQuantityShippedTotal(quantityExpected: Long) =
        Assertions.assertThat(actual.stockQuantities.values.sumOf { it.quantityShipped }).isEqualTo(quantityExpected)
            .also {
                Assertions.assertThat(actual.orderQuantities.values.sumOf { it.quantityShipped }).isEqualTo(quantityExpected)
            }
            .let { this }

    fun chargebacksNotPresent() =
        Assertions.assertThat(actual.chargebackAmountDelta).isEqualByComparingTo(BigDecimal.ZERO)
            .let { this }

    fun chargebacksPresent() =
        Assertions.assertThat(actual.chargebackAmountDelta).isGreaterThan(BigDecimal.ZERO)
            .let { this }

    companion object {
        fun assertThat(actual: SnapshotStateDelta) =
            SnapshotStateDeltaAssert(actual, SnapshotStateDeltaAssert::class.java)

        private val log = KotlinLogging.logger {}
    }
}
