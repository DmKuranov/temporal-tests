package ru.dmkuranov.temporaltests.core.customerorder.dto

import ru.dmkuranov.temporaltests.core.stock.dto.ProductDto

data class CustomerOrderItemDto(
    val itemId: Long,
    val product: ProductDto,
    val quantityOrdered: Long,
    val quantityReserved: Long,
    val quantityShipped: Long
)
