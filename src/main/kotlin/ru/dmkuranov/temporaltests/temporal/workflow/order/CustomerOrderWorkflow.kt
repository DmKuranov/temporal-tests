package ru.dmkuranov.temporaltests.temporal.workflow.order

import io.temporal.workflow.WorkflowInterface
import io.temporal.workflow.WorkflowMethod
import ru.dmkuranov.temporaltests.core.customerorder.dto.CustomerOrderCreateRequestDto

@WorkflowInterface
interface CustomerOrderWorkflow {
    @WorkflowMethod
    fun process(orderRequest: CustomerOrderCreateRequestDto): CustomerOrderWorkflowResultDto
}
