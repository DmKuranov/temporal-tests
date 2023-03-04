package ru.dmkuranov.temporaltests.core.customerorder.dto

import ru.dmkuranov.temporaltests.core.stock.dto.ProductDto

data class CustomerOrderItemCreateRequestDto(
    val product: ProductDto,
    val quantity: Long
)
