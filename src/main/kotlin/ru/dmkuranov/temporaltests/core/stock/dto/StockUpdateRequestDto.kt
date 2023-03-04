package ru.dmkuranov.temporaltests.core.stock.dto

import ru.dmkuranov.temporaltests.core.stock.dto.StockDto

data class StockUpdateRequestDto(
    val productId: Long,
    val quantityStock: Long,
    val quantityReserved: Long,
    val available: Boolean
) {
    constructor(
        stockDto: StockDto
    ) : this(
        productId = stockDto.productId,
        quantityStock = stockDto.quantityStock,
        quantityReserved = stockDto.quantityReserved,
        available = stockDto.available
    )
}
