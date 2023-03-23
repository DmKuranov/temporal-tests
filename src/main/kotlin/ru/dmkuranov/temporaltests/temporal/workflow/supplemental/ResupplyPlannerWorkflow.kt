package ru.dmkuranov.temporaltests.temporal.workflow.supplemental

import io.temporal.workflow.SignalMethod
import io.temporal.workflow.WorkflowInterface
import io.temporal.workflow.WorkflowMethod
import ru.dmkuranov.temporaltests.core.stock.dto.ProductDto

@WorkflowInterface
interface ResupplyPlannerWorkflow : ShutdownableWorkflow {
    @WorkflowMethod
    fun planResupply(products: List<ProductDto>, stealerEnabled: Boolean)

    @SignalMethod
    override fun shutdown()

    companion object {
        const val WORKFLOW_NAME = "resupplyPlanner"
    }
}
