package ru.dmkuranov.temporaltests.temporal.workflow.supplemental

import io.temporal.activity.ActivityInterface
import ru.dmkuranov.temporaltests.core.stock.dto.ProductDto

@ActivityInterface
interface SupplementalActivities {
    fun calculateResupplyQuantity(product: ProductDto): Long
    fun scheduleResupply(product: ProductDto, quantity: Long)
    fun performResupply(product: ProductDto)
    fun performSteal(products: List<ProductDto>)
}
