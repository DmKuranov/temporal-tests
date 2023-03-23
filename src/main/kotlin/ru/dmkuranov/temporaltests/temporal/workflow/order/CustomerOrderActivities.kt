package ru.dmkuranov.temporaltests.temporal.workflow.order

import io.temporal.activity.ActivityInterface
import ru.dmkuranov.temporaltests.core.charge.dto.ChargeDto
import ru.dmkuranov.temporaltests.core.charge.dto.ChargeRequestDto
import ru.dmkuranov.temporaltests.core.customerorder.dto.CustomerOrderCreateRequestDto

@ActivityInterface
interface CustomerOrderActivities {

    fun createOrder(orderCreateRequest: CustomerOrderCreateRequestDto): Long
    fun reserveItems(orderId: Long): Boolean
    fun performChargeOnReserve(orderId: Long): ChargeDto
    fun shipItems(orderId: Long): ChargeRequestDto?
    fun performCharge(chargeRequest: ChargeRequestDto): ChargeDto
    fun completeOrder(orderId: Long): Unit
}
