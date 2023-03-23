package ru.dmkuranov.temporaltests.temporal.workflow.supplemental

import io.temporal.workflow.WorkflowInterface
import io.temporal.workflow.WorkflowMethod
import ru.dmkuranov.temporaltests.core.stock.dto.ProductDto

@WorkflowInterface
interface ResupplyWorkflow {
    @WorkflowMethod
    fun resupply(product: ProductDto)
}
