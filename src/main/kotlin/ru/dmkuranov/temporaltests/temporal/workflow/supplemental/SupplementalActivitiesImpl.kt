package ru.dmkuranov.temporaltests.temporal.workflow.supplemental

import io.temporal.client.WorkflowClient
import io.temporal.client.WorkflowOptions
import io.temporal.spring.boot.ActivityImpl
import mu.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import ru.dmkuranov.temporaltests.core.stock.StockService
import ru.dmkuranov.temporaltests.core.stock.dto.ProductDto
import ru.dmkuranov.temporaltests.core.stock.dto.StockUpdateRequestDto
import ru.dmkuranov.temporaltests.temporal.TemporalConfiguration

@Component
@ActivityImpl(taskQueues = [TemporalConfiguration.TASK_QUEUE_NAME])
class SupplementalActivitiesImpl(
    private val stockService: StockService,
    private val workflowClient: WorkflowClient
) : SupplementalActivities {

    @Transactional(readOnly = true)
    override fun calculateResupplyQuantity(product: ProductDto): Long {
        val stock = stockService.getStock(product)
        if (stock.quantitySupplyAwaiting == 0L) {
            val supplyQuantityRequirement = stockService.calculateSupplyQuantityRequirement(product)
            if (supplyQuantityRequirement > stock.quantityAvailable) {
                val resupplyQuantity = supplyQuantityRequirement - stock.quantityAvailable
                log.info { "Resupplying [${product.productId}] for $resupplyQuantity" }
                return resupplyQuantity
            } else {
                log.info { "Resupplying [${product.productId}]: no resupply required" }
            }
        }
        return 0
    }

    @Transactional
    override fun scheduleResupply(product: ProductDto, quantity: Long) {
        stockService.updateStock(StockUpdateRequestDto(product).copy(quantitySupplyAwaiting = quantity))
        val workflow = workflowClient.newWorkflowStub(
            ResupplyWorkflow::class.java,
            WorkflowOptions.getDefaultInstance()
                .toBuilder()
                .setTaskQueue(TemporalConfiguration.TASK_QUEUE_NAME)
                .setWorkflowId("resupply-${product.productId}")
                .build()
        )
        WorkflowClient.start(workflow::resupply, product)
        log.info { "Resupplying [${product.productId}] for $quantity: stock updating" }
    }

    override fun performResupply(product: ProductDto) =
        stockService.resupplyFromAwaiting(productId = product.productId)
            .also { log.info { "Resupplied [${product.productId}] from awaiting" } }

    @Transactional
    override fun performSteal(products: List<ProductDto>) {
        val stock = products.shuffled().map { stockService.getStock(it) }.firstOrNull {
            it.quantityReserved > 0
        }
        if (stock != null) {
            val updatedStock = stockService.updateStock(
                StockUpdateRequestDto(stock)
                    .copy(quantityAvailable = (stock.quantityReserved - 1))
            )
            log.info { "Stealing [${stock.productId}] ${stock.quantityAvailable}->${updatedStock.quantityAvailable}" }
        }
    }

    companion object {
        private val log = KotlinLogging.logger {}
    }
}
