package ru.dmkuranov.temporaltests.temporal.workflow.supplemental

import io.temporal.spring.boot.WorkflowImpl
import io.temporal.workflow.Workflow
import mu.KotlinLogging
import ru.dmkuranov.temporaltests.core.stock.dto.ProductDto
import ru.dmkuranov.temporaltests.temporal.TemporalConfiguration

@WorkflowImpl(taskQueues = [TemporalConfiguration.TASK_QUEUE_NAME])
class StealerWorkflowImpl : StealerWorkflow {

    var resupplyFinished = false
    var running = true

    override fun startSteal(products: List<ProductDto>) {
        Workflow.await { resupplyFinished || !running }
        activities.performSteal(products)
        if (running) {
            Workflow.continueAsNew(products)
        } else {
            log.info { "Stealer workflow exited" }
        }
    }

    override fun onResupplyFinished() {
        resupplyFinished = true
    }

    override fun shutdown() {
        running = false
    }

    private val activities = Workflow.newActivityStub(
        SupplementalActivities::class.java,
        TemporalConfiguration.ACTIVITY_OPTIONS_DEFAULT
    )!!

    companion object {
        private val log = KotlinLogging.logger {}
    }
}
