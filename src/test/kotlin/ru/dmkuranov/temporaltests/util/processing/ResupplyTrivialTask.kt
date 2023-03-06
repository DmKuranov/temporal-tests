package ru.dmkuranov.temporaltests.util.processing

import mu.KotlinLogging
import org.springframework.core.task.AsyncTaskExecutor
import ru.dmkuranov.temporaltests.core.stock.StockService
import ru.dmkuranov.temporaltests.core.stock.dto.ProductDto
import ru.dmkuranov.temporaltests.core.stock.dto.StockUpdateRequestDto
import ru.dmkuranov.temporaltests.util.invocation.Invoker
import ru.dmkuranov.temporaltests.util.invocation.TransactionalInvoker
import java.util.concurrent.atomic.AtomicBoolean

class ResupplyTrivialTask(
    private val products: List<ProductDto>,
    private val running: AtomicBoolean,
    private val executor: AsyncTaskExecutor,
    private val transactionalInvoker: TransactionalInvoker<Any?>,
    private val stockService: StockService
) : Runnable {
    private val rescanPeriodMs = 100L
    override fun run() {
        while (running.get()) {
            try {
                log.info { "Resupply started" }
                products.forEach { product ->
                    Invoker.dbConcurrencyRetry.invoke {
                        if (running.get()) {
                            val stock = stockService.getStock(product)
                            if (stock.quantitySupplyAwaiting == 0L) {
                                val supplyQuantityRequirement = stockService.calculateSupplyQuantityRequirement(product)
                                if (supplyQuantityRequirement > stock.quantityAvailable) {
                                    val resupplyQuantity = supplyQuantityRequirement - stock.quantityAvailable
                                    log.info { "Resupplying [${product.productId}] for $resupplyQuantity" }
                                    transactionalInvoker.invoke {
                                        stockService.updateStock(StockUpdateRequestDto(product).copy(quantitySupplyAwaiting = resupplyQuantity))
                                        log.info { "Resupplying [${product.productId}] for $resupplyQuantity: stock updated" }
                                    }
                                    executor.submit {
                                        try {
                                            Invoker.dbConcurrencyRetry.invoke {
                                                if (running.get()) {
                                                    stockService.resupplyFromAwaiting(product.productId)
                                                    log.info { "Resupplied [${product.productId}] from awaiting" }
                                                }
                                            }
                                        } catch (e: Exception) {
                                            log.error(e) { "Resupply from awaiting problem" }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                log.error(e) { "Resupply task problem" }
            }
            log.info { "Resupply completed" }
            Thread.sleep(rescanPeriodMs)
        }
        log.info { "Resupply task exited" }
    }

    private val log = KotlinLogging.logger {}
}
