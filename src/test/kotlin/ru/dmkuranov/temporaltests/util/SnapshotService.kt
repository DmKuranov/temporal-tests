package ru.dmkuranov.temporaltests.util

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.dmkuranov.temporaltests.util.dto.OrderQuantityAggregate
import ru.dmkuranov.temporaltests.util.dto.SnapshotState
import ru.dmkuranov.temporaltests.core.charge.db.CustomerChargeRepository
import ru.dmkuranov.temporaltests.core.customerorder.db.CustomerOrderItemRepository
import ru.dmkuranov.temporaltests.core.customerorder.db.CustomerOrderRepository
import ru.dmkuranov.temporaltests.core.stock.StockMapper
import ru.dmkuranov.temporaltests.core.stock.db.StockRepository
import java.math.BigDecimal

@Service
class SnapshotService(
    private val stockRepository: StockRepository,
    private val customerOrderItemRepository: CustomerOrderItemRepository,
    private val customerOrderRepository: CustomerOrderRepository,
    private val chargeRepository: CustomerChargeRepository,
    private val stockMapper: StockMapper
) {
    @Transactional(readOnly = true)
    fun createSnapshot(): SnapshotState {
        val stocksMap = stockRepository.findAll().associate { it.id!! to stockMapper.map(it) }
        val orderQuantities = customerOrderItemRepository.findQuantityByProduct()
            .associateBy({ it.getProductId() }, {
                OrderQuantityAggregate(
                    quantityOrdered = it.getQuantityOrderedSum(),
                    quantityReserved = it.getQuantityReservedSum(),
                    quantityShipped = it.getQuantityShippedSum()
                )
            })
        val chargeAmounts = chargeRepository.findAll().map { it.amount!! }
        val chargeAmount = chargeAmounts.sumOf { it }
        val chargebackAmount = chargeAmounts.filter { it < BigDecimal.ZERO }.sumOf { it }.negate()
        return SnapshotState(
            stocks = stocksMap,
            orderQuantities = orderQuantities,
            chargeAmount = chargeAmount,
            chargebackAmount = chargebackAmount,
            orderCount = customerOrderRepository.findCompletedOrderCount()
        )
    }
}
