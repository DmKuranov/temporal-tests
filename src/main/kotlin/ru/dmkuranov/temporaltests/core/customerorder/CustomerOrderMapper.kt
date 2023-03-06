package ru.dmkuranov.temporaltests.core.customerorder

import org.springframework.stereotype.Component
import ru.dmkuranov.temporaltests.core.customerorder.db.CustomerOrderEntity
import ru.dmkuranov.temporaltests.core.customerorder.db.CustomerOrderItemEntity
import ru.dmkuranov.temporaltests.core.customerorder.dto.CustomerOrderDto
import ru.dmkuranov.temporaltests.core.customerorder.dto.CustomerOrderItemDto
import ru.dmkuranov.temporaltests.core.stock.dto.ProductDto

@Component
class CustomerOrderMapper {
    fun map(entity: CustomerOrderEntity): CustomerOrderDto =
        CustomerOrderDto(
            id = entity.id!!,
            paymentCredential = entity.paymentCredential!!,
            items = entity.items!!.map { map(it) },
            submittedAt = entity.submittedAt!!,
            completedAt = entity.completedAt
        )

    fun map(entityItem: CustomerOrderItemEntity): CustomerOrderItemDto =
        CustomerOrderItemDto(
            itemId = entityItem.id!!,
            orderId = entityItem.order!!.id!!,
            product = ProductDto(entityItem.productId!!),
            quantityOrdered = entityItem.quantityOrdered!!,
            quantityReserved = entityItem.quantityReserved!!,
            quantityShipped = entityItem.quantityShipped!!
        )
}
