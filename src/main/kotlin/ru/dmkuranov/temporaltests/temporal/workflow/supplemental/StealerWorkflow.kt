package ru.dmkuranov.temporaltests.temporal.workflow.supplemental

import io.temporal.workflow.SignalMethod
import io.temporal.workflow.WorkflowInterface
import io.temporal.workflow.WorkflowMethod
import ru.dmkuranov.temporaltests.core.stock.dto.ProductDto

@WorkflowInterface
interface StealerWorkflow : ShutdownableWorkflow {
    @WorkflowMethod
    fun startSteal(products: List<ProductDto>)

    @SignalMethod
    fun onResupplyFinished()

    @SignalMethod
    override fun shutdown()

    companion object {
        const val WORKFLOW_NAME = "stealer"
    }
}
