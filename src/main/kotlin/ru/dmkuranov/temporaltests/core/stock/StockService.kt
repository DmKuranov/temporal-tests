package ru.dmkuranov.temporaltests.core.stock

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.dmkuranov.temporaltests.core.stock.db.StockEntity
import ru.dmkuranov.temporaltests.core.stock.db.StockRepository
import ru.dmkuranov.temporaltests.core.stock.dto.ProductDto
import ru.dmkuranov.temporaltests.core.stock.dto.StockDto
import ru.dmkuranov.temporaltests.core.stock.dto.StockUpdateRequestDto

@Service
class StockService(
    private val stockRepository: StockRepository,
    private val stockMapper: StockMapper
) {
    @Transactional
    fun createProduct(): ProductDto =
        stockRepository.save(StockEntity(quantityStock = 0, quantityReserved = 0, available = false))
            .let { stockMapper.mapToProductDto(it) }

    @Transactional(readOnly = true)
    fun getProductsAvailable(): List<ProductDto> =
        stockRepository.findByAvailableIsTrue()
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
                entity.quantityStock = request.quantityStock
                entity.quantityReserved = request.quantityReserved
                entity.available = request.available
                stockRepository.save(entity)
            }
            .let { stockMapper.map(it) }

    private fun loadStock(productId: Long): StockEntity =
        stockRepository.findById(productId)
            .let {
                if (it.isPresent)
                    it.get()
                else
                    throw IllegalArgumentException("Stock not found for productId=$productId")
            }
}
