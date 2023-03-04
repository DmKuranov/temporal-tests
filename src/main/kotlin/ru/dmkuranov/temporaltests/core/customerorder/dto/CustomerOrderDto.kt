package ru.dmkuranov.temporaltests.core.customerorder.dto

import java.time.LocalDateTime

data class CustomerOrderDto(
    val id: Long,
    val items: List<CustomerOrderItemDto>,
    val paymentCredential: String,
    val submittedAt: LocalDateTime
)
