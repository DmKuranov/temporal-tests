package ru.dmkuranov.temporaltests.core.customerorder.db

import org.jetbrains.annotations.TestOnly
import org.springframework.data.jpa.repository.JpaRepository
import ru.dmkuranov.temporaltests.core.customerorder.db.CustomerOrderItemEntity
import org.springframework.data.jpa.repository.Query
import java.time.LocalDateTime

interface CustomerOrderItemRepository : JpaRepository<CustomerOrderItemEntity, Long> {
    @Query(
        nativeQuery = true,
        value = "select date_trunc('milliseconds', i.order_submitted_at) as orderSubmittedAt, sum(i.quantity_ordered) as quantityOrderedSum" +
            " from CUSTOMER_ORDER_ITEM I where i.product_id = :productId and i.order_submitted_at>:orderSubmittedAfter" +
            " group by date_trunc('milliseconds', i.order_submitted_at)"
    )
    fun findOrderedQuantityByProduct(productId: Long, orderSubmittedAfter: LocalDateTime): List<DateAndCount>

    interface DateAndCount {
        fun getOrderSubmittedAt(): LocalDateTime
        fun getQuantityOrderedSum(): Long
    }

    @TestOnly
    @Query(
        "select e.productId as productId," +
            " sum(e.quantityOrdered) as quantityOrderedSum," +
            " sum(e.quantityReserved) as quantityReservedSum," +
            " sum(e.quantityShipped) as quantityShippedSum" +
            " from CustomerOrderItemEntity e group by e.productId"
    )
    fun findQuantityByProduct(): List<ProductQuantity>

    interface ProductQuantity {
        fun getProductId(): Long
        fun getQuantityOrderedSum(): Long
        fun getQuantityReservedSum(): Long
        fun getQuantityShippedSum(): Long
    }
}
