package ru.dmkuranov.temporaltests.core.customerorder.dto

data class CustomerOrderCreateRequestDto(
    val items: List<CustomerOrderItemCreateRequestDto>,
    val paymentCredential: String
)
