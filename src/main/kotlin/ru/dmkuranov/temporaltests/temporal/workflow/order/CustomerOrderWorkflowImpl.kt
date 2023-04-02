package ru.dmkuranov.temporaltests.temporal.workflow.order

import io.temporal.spring.boot.WorkflowImpl
import io.temporal.workflow.Saga
import io.temporal.workflow.Workflow
import mu.KotlinLogging
import ru.dmkuranov.temporaltests.core.customerorder.dto.CustomerOrderCreateRequestDto
import ru.dmkuranov.temporaltests.temporal.TemporalConfiguration
import ru.dmkuranov.temporaltests.core.charge.dto.ChargeRequestDto
import java.math.BigDecimal

@WorkflowImpl(taskQueues = [TemporalConfiguration.TASK_QUEUE_NAME])
class CustomerOrderWorkflowImpl : CustomerOrderWorkflow {

    override fun process(orderRequest: CustomerOrderCreateRequestDto): CustomerOrderWorkflowResultDto {
        val orderId = activities.createOrder(orderRequest)
        val saga = Saga(
            Saga.Options.Builder()
                .setParallelCompensation(false)
                .build()
        )
        try {
            var chargeAmount = BigDecimal.ZERO
            val valid: Boolean
            if (activities.reserveItems(orderId)) {
                saga.addCompensation { activities.cancelReserve(orderId) }
                valid = true
                val charge = activities.performChargeOnReserve(orderId)
                chargeAmount = chargeAmount.plus(charge.amount)
                saga.addCompensation {
                    activities.performChargeSafe(
                        ChargeRequestDto(
                            orderId = orderId,
                            amount = chargeAmount.negate(),
                            note = "failure compensating chargeback"
                        )
                    )
                }
                val chargebackRequest = activities.shipItems(orderId)
                saga.addCompensation { activities.cancelShipping(orderId) }
                if (chargebackRequest != null) {
                    val chargebackDto = activities.performCharge(chargebackRequest)
                    chargeAmount = chargeAmount.plus(chargebackDto.amount)
                }
            } else {
                valid = false
            }
            activities.completeOrder(orderId)
            return CustomerOrderWorkflowResultDto(
                orderId = orderId,
                valid = valid
            )
        } catch (e: Exception) {
            log.error(e) { "CustomerOrderWorkflow id=$orderId failure, compensating" }
            Workflow.newDetachedCancellationScope {
                try {
                    saga.compensate()
                    activities.completeOrder(orderId)
                    log.info { "CustomerOrderWorkflow id=$orderId failure compensated" }
                } catch (e: Exception) {
                    log.error(e) { "CustomerOrderWorkflow id=$orderId compensation failure" }
                    throw e
                }
            }.run()
            return CustomerOrderWorkflowResultDto(
                orderId = orderId,
                failed = true
            )
        }
    }

    private val activities = Workflow.newActivityStub(
        CustomerOrderActivities::class.java,
        TemporalConfiguration.ACTIVITY_OPTIONS_DEFAULT
    )!!

    companion object {
        private val log = KotlinLogging.logger {}
    }
}
