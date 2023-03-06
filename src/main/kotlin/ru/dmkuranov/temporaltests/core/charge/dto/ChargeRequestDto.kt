package ru.dmkuranov.temporaltests.core.charge.dto

import java.math.BigDecimal

data class ChargeRequestDto(
    val orderId: Long,
    val amount: BigDecimal,
    val note: String,
)
