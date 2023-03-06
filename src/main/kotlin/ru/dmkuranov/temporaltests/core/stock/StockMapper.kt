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
            quantityAvailable = entity.quantityAvailable!!,
            quantityReserved = entity.quantityReserved!!,
            quantityShipped = entity.quantityShipped!!,
            quantitySupplyAwaiting = entity.quantitySupplyAwaiting!!,
            available = entity.available!!,
            price = entity.price!!
        )
}
