package ru.dmkuranov.temporaltests.util.dto

import java.math.BigDecimal

data class SnapshotStateDelta(
    val stockQuantities: Map<Long, StockQuantityAggregate>,
    val orderQuantities: Map<Long, OrderQuantityAggregate>,
    val chargeAmountShipped: BigDecimal,
    val chargeAmountCharged: BigDecimal,
    val chargebackAmountDelta: BigDecimal,
    val orderCount: Int
)
