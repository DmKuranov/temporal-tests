package ru.dmkuranov.temporaltests.core.stock.dto

import ru.dmkuranov.temporaltests.core.stock.dto.StockDto

import java.math.BigDecimal

data class StockUpdateRequestDto(
    val productId: Long,
    val quantityStock: Long? = null,
    val quantityReserved: Long? = null,
    val available: Boolean? = null,
    val price: BigDecimal? = null
) {
    constructor(
        stockDto: StockDto
    ) : this(
        productId = stockDto.productId
    )

    constructor(
        productDto: ProductDto
    ) : this(
        productId = productDto.productId
    )
}
