package ru.dmkuranov.temporaltests.temporal.workflow.order

import io.temporal.spring.boot.ActivityImpl
import org.springframework.stereotype.Component
import ru.dmkuranov.temporaltests.core.charge.ChargeService
import ru.dmkuranov.temporaltests.core.charge.dto.ChargeRequestDto
import ru.dmkuranov.temporaltests.core.customerorder.CustomerOrderService
import ru.dmkuranov.temporaltests.core.customerorder.dto.CustomerOrderCreateRequestDto
import ru.dmkuranov.temporaltests.core.processing.FulfillmentService
import ru.dmkuranov.temporaltests.temporal.TemporalConfiguration

@Component
@ActivityImpl(taskQueues = [TemporalConfiguration.TASK_QUEUE_NAME])
class CustomerOrderActivitiesImpl(
    private val customerOrderService: CustomerOrderService,
    private val fulfillmentService: FulfillmentService,
    private val chargeService: ChargeService
) : CustomerOrderActivities {

    override fun createOrder(orderCreateRequest: CustomerOrderCreateRequestDto) =
        customerOrderService.createOrder(orderCreateRequest)

    override fun reserveItems(orderId: Long) =
        fulfillmentService.reserveItems(orderId)

    override fun performChargeOnReserve(orderId: Long) =
        chargeService.performChargeOnReserve(orderId)

    override fun shipItems(orderId: Long) =
        fulfillmentService.shipItems(orderId)

    override fun performCharge(chargeRequest: ChargeRequestDto) =
        chargeService.performCharge(chargeRequest)

    override fun completeOrder(orderId: Long) =
        customerOrderService.completeOrder(orderId)

    override fun performChargeSafe(chargeRequest: ChargeRequestDto) =
        chargeService.performChargeSafe(chargeRequest)

    override fun cancelReserve(orderId: Long) =
        fulfillmentService.cancelReserve(orderId)

    override fun cancelShipping(orderId: Long) =
        fulfillmentService.cancelShipping(orderId)
}
