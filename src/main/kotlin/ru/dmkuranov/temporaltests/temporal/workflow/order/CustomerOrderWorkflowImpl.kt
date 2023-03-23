package ru.dmkuranov.temporaltests.temporal.workflow.order

import io.temporal.spring.boot.WorkflowImpl
import io.temporal.workflow.Workflow
import ru.dmkuranov.temporaltests.core.customerorder.dto.CustomerOrderCreateRequestDto
import ru.dmkuranov.temporaltests.temporal.TemporalConfiguration

@WorkflowImpl(taskQueues = [TemporalConfiguration.TASK_QUEUE_NAME])
class CustomerOrderWorkflowImpl : CustomerOrderWorkflow {

    override fun process(orderRequest: CustomerOrderCreateRequestDto): CustomerOrderWorkflowResultDto {
        val orderId = activities.createOrder(orderRequest)
        val valid: Boolean
        if (activities.reserveItems(orderId)) {
            valid = true
            activities.performChargeOnReserve(orderId)
            val chargebackRequest = activities.shipItems(orderId)
            if (chargebackRequest != null) {
                activities.performCharge(chargebackRequest)
            }
        } else {
            valid = false
        }
        activities.completeOrder(orderId)
        return CustomerOrderWorkflowResultDto(
            orderId = orderId,
            valid = valid
        )
    }

    private val activities = Workflow.newActivityStub(
        CustomerOrderActivities::class.java,
        TemporalConfiguration.ACTIVITY_OPTIONS_DEFAULT
    )!!
}
