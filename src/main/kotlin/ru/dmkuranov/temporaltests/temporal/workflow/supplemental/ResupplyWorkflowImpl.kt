package ru.dmkuranov.temporaltests.temporal.workflow.supplemental

import io.temporal.spring.boot.WorkflowImpl
import io.temporal.workflow.Workflow
import ru.dmkuranov.temporaltests.core.stock.dto.ProductDto
import ru.dmkuranov.temporaltests.temporal.TemporalConfiguration

@WorkflowImpl(taskQueues = [TemporalConfiguration.TASK_QUEUE_NAME])
class ResupplyWorkflowImpl : ResupplyWorkflow {
    override fun resupply(product: ProductDto) {
        activities.performResupply(product)
    }

    private val activities = Workflow.newActivityStub(
        SupplementalActivities::class.java,
        TemporalConfiguration.ACTIVITY_OPTIONS_DEFAULT
    )!!
}
