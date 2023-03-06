package ru.dmkuranov.temporaltests.util.dto

data class StockQuantityAggregate(
    val quantityAvailable: Long,
    val quantityReserved: Long,
    val quantityShipped: Long
)
