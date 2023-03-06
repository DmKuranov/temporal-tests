package ru.dmkuranov.temporaltests.core.stock.dto

import java.math.BigDecimal

data class StockDto(
    val productId: Long,
    val quantityAvailable: Long,
    val quantityReserved: Long,
    val quantityShipped: Long,
    val quantitySupplyAwaiting: Long,
    val available: Boolean,
    val price: BigDecimal
)
