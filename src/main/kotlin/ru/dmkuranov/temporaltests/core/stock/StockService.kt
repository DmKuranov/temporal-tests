package ru.dmkuranov.temporaltests.core.stock

import mu.KotlinLogging
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.dmkuranov.temporaltests.core.customerorder.db.CustomerOrderItemRepository
import ru.dmkuranov.temporaltests.core.stock.db.StockEntity
import ru.dmkuranov.temporaltests.core.stock.db.StockRepository
import ru.dmkuranov.temporaltests.core.stock.dto.ProductDto
import ru.dmkuranov.temporaltests.core.stock.dto.StockDto
import ru.dmkuranov.temporaltests.core.stock.dto.StockUpdateRequestDto
import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime

@Service
class StockService(
    private val stockRepository: StockRepository,
    private val stockMapper: StockMapper,
    private val customerOrderItemRepository: CustomerOrderItemRepository,
    private val clock: Clock
) {
    @Transactional
    fun createProduct(quantity: Long = 0, price: BigDecimal = BigDecimal.ZERO): ProductDto {
        val available = quantity > 0
        val entity = stockRepository.save(
            with(StockEntity()) {
                quantityAvailable = quantity
                quantityReserved = 0
                quantityShipped = 0
                quantitySupplyAwaiting = 0
                this.available = available
                this.price = price
                supplyAt = if (available) LocalDateTime.now(clock) else null
                this
            }
        )
        return stockMapper.mapToProductDto(entity)
    }

    @Transactional(readOnly = true)
    @Cacheable("productsAvailableById")
    fun getProductsAvailable(products: List<ProductDto>): List<ProductDto> =
        stockRepository.findByAvailableIsTrueAndIdIn(products.map { it.productId })
            .map { stockMapper.mapToProductDto(it) }

    @Transactional(readOnly = true)
    fun getStock(productId: Long): StockDto =
        loadStock(productId)
            .let { stockMapper.map(it) }

    @Transactional(readOnly = true)
    fun getStock(productDto: ProductDto): StockDto =
        getStock(productDto.productId)

    @Transactional
    fun updateStock(request: StockUpdateRequestDto): StockDto =
        loadStock(request.productId)
            .let { entity ->
                log.debug { "Updating stock with $request" }
                with(request) {
                    if (quantityAvailable != null) {
                        entity.quantityAvailable = quantityAvailable
                    }
                    if (quantityReserved != null) {
                        entity.quantityReserved = quantityReserved
                    }
                    if (quantityShipped != null) {
                        entity.quantityShipped = quantityShipped
                    }
                    if (quantitySupplyAwaiting != null) {
                        entity.quantitySupplyAwaiting = quantitySupplyAwaiting
                    }
                    if (available != null) {
                        entity.available = available
                    }
                }
                stockRepository.save(entity)
            }
            .let { stockMapper.map(it) }

    @Transactional(readOnly = true)
    fun calculateSupplyQuantityRequirement(product: ProductDto): Long {
        val now = LocalDateTime.now(clock)
        val orderedByDt = customerOrderItemRepository.findOrderedQuantityByProduct(
            product.productId,
            now.minus(supplyPlanningDuration.multipliedBy(retrospectiveToPlanningDurationMultiplier))
        )
            .associate { it.getOrderSubmittedAt() to it.getQuantityOrderedSum() }
        if (orderedByDt.isNotEmpty()) {
            val grouped = groupByInterval(now, groupIntervalDuration, orderedByDt)
            val maxPerInterval = grouped.values.max()
            val supplyPlanningGroupCount = supplyPlanningDuration.dividedBy(groupIntervalDuration)
            return supplyPlanningGroupCount * maxPerInterval
        }
        return 0
    }

    @Transactional
    fun resupplyFromAwaiting(productId: Long) {
        loadStock(productId)
            .let {
                it.available = true
                it.quantityAvailable = it.quantityAvailable!! + it.quantitySupplyAwaiting!!
                it.quantitySupplyAwaiting = 0
                it.supplyAt = LocalDateTime.now(clock)
                stockRepository.save(it)
            }
    }

    private fun loadStock(productId: Long): StockEntity =
        stockRepository.findById(productId)
            .let {
                if (it.isPresent)
                    it.get()
                else
                    throw IllegalArgumentException("Stock not found for productId=$productId")
            }

    fun groupByInterval(
        now: LocalDateTime,
        intervalDuration: Duration,
        countByInterval: Map<LocalDateTime, Long>
    ): Map<LocalDateTime, Long> {
        val earliest = countByInterval.keys.min()
        return generateSequence(now) { it.minus(intervalDuration).let { bound -> if (bound >= earliest) bound else null } }
            .map { upperBound ->
                upperBound to countByInterval.filter {
                    !it.key.isBefore(upperBound.minus(intervalDuration)) && it.key.isBefore(
                        upperBound
                    )
                }.values.sum()
            }
            .toMap()
    }

    companion object {
        private val groupIntervalDuration = Duration.ofMillis(200)
        private val supplyPlanningDuration = Duration.ofSeconds(2)
        private const val retrospectiveToPlanningDurationMultiplier = 10L

        private val log = KotlinLogging.logger {}
    }
}
