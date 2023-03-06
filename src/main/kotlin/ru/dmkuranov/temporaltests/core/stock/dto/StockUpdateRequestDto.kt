package ru.dmkuranov.temporaltests.core.stock.dto

import ru.dmkuranov.temporaltests.core.stock.dto.StockDto

data class StockUpdateRequestDto(
    val productId: Long,
    val quantityAvailable: Long? = null,
    val quantityReserved: Long? = null,
    val quantityShipped: Long? = null,
    val quantitySupplyAwaiting: Long? = null,
    val available: Boolean? = null
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
