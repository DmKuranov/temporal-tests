package ru.dmkuranov.temporaltests.core.charge.dto

import java.math.BigDecimal

data class ChargeDto(
    val amount: BigDecimal,
    val orderId: Long
)
