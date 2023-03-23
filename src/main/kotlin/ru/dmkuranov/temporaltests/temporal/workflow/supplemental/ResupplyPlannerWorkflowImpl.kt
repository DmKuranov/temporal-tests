package ru.dmkuranov.temporaltests.temporal.workflow.supplemental

import io.temporal.api.common.v1.WorkflowExecution
import io.temporal.client.WorkflowFailedException
import io.temporal.spring.boot.WorkflowImpl
import io.temporal.workflow.Workflow
import io.temporal.workflow.Workflow.sleep
import mu.KotlinLogging
import ru.dmkuranov.temporaltests.core.stock.dto.ProductDto
import ru.dmkuranov.temporaltests.temporal.TemporalConfiguration

@WorkflowImpl(taskQueues = [TemporalConfiguration.TASK_QUEUE_NAME])
class ResupplyPlannerWorkflowImpl : ResupplyPlannerWorkflow {

    var running = true

    override fun planResupply(products: List<ProductDto>, stealerEnabled: Boolean) {
        products.forEach { product ->
            val resupplyQuantity = activities.calculateResupplyQuantity(product)
            if (resupplyQuantity > 0)
                activities.scheduleResupply(product, resupplyQuantity)
        }

        if (stealerEnabled && running) {
            val stealer = Workflow.newExternalWorkflowStub(
                StealerWorkflow::class.java,
                WorkflowExecution.getDefaultInstance().toBuilder()
                    .setWorkflowId(StealerWorkflow.WORKFLOW_NAME)
                    .build()
            )
            try {
                stealer.onResupplyFinished()
            } catch (@Suppress("SwallowedException") e: WorkflowFailedException) {
                // swallow it
                // stealer workflow could be completed
            }
        }

        sleep(rescanPeriodMs)
        if (running) {
            Workflow.continueAsNew(products, stealerEnabled)
        } else {
            log.info { "Resupply workflow exited" }
        }
    }

    override fun shutdown() {
        running = false
    }

    private val activities = Workflow.newActivityStub(
        SupplementalActivities::class.java,
        TemporalConfiguration.ACTIVITY_OPTIONS_DEFAULT
    )!!

    companion object {
        private const val rescanPeriodMs = 200L

        private val log = KotlinLogging.logger {}
    }
}
