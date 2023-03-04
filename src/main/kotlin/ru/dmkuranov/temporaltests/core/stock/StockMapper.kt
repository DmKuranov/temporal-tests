package ru.dmkuranov.temporaltests.core.stock

import org.springframework.stereotype.Component
import ru.dmkuranov.temporaltests.core.stock.db.StockEntity
import ru.dmkuranov.temporaltests.core.stock.dto.ProductDto
import ru.dmkuranov.temporaltests.core.stock.dto.StockDto

@Component
class StockMapper {
    fun mapToProductDto(entity: StockEntity): ProductDto =
        ProductDto(entity.id!!)

    fun map(entity: StockEntity): StockDto =
        StockDto(
            productId = entity.id!!,
            quantityStock = entity.quantityStock!!,
            quantityReserved = entity.quantityReserved!!,
            available = entity.available!!
        )
}
