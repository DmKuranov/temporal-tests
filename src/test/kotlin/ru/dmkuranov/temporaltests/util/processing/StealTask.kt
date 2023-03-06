package ru.dmkuranov.temporaltests.util.processing

import mu.KotlinLogging
import ru.dmkuranov.temporaltests.util.invocation.Invoker
import ru.dmkuranov.temporaltests.util.invocation.TransactionalInvoker
import ru.dmkuranov.temporaltests.util.supplier.OrderCreateRequestSupplier
import ru.dmkuranov.temporaltests.core.stock.StockService
import ru.dmkuranov.temporaltests.core.stock.dto.StockUpdateRequestDto
import java.util.concurrent.atomic.AtomicBoolean

class StealTask(
    private val orderCreateRequestSupplier: OrderCreateRequestSupplier,
    private val running: AtomicBoolean,
    private val transactionalInvoker: TransactionalInvoker<Any?>,
    private val stockService: StockService
) : Runnable {
    private val rescanPeriodMs = 300L
    override fun run() {
        while (running.get()) {
            try {
                val product = orderCreateRequestSupplier.products.shuffled().firstOrNull {
                    stockService.getStock(it).quantityReserved > 0
                }
                if (product != null) {
                    Invoker.dbConcurrencyRetry.invoke {
                        if (running.get()) {
                            transactionalInvoker.invoke {
                                val stock = stockService.getStock(product)
                                if (stock.quantityReserved > 0) {
                                    val updatedStock = stockService.updateStock(
                                        StockUpdateRequestDto(product)
                                            .copy(quantityAvailable = (stock.quantityReserved - 1))
                                    )
                                    log.info { "Stealing [${product.productId}] ${stock.quantityAvailable}->${updatedStock.quantityAvailable}" }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                log.error(e) { "Steal task problem" }
            }
            Thread.sleep(rescanPeriodMs)
        }
        log.info { "Steal task exited" }
    }

    private val log = KotlinLogging.logger {}
}
