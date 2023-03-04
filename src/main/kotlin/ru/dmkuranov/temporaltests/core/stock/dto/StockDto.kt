package ru.dmkuranov.temporaltests.core.stock.dto

import java.math.BigDecimal

data class StockDto(
    val productId: Long,
    val quantityStock: Long,
    val quantityReserved: Long,
    val available: Boolean,
    val price: BigDecimal
)
