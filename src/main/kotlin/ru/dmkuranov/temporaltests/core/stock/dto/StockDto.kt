package ru.dmkuranov.temporaltests.core.stock.dto

data class StockDto(
    val productId: Long,
    val quantityStock: Long,
    val quantityReserved: Long,
    val available: Boolean
)
