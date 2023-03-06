package ru.dmkuranov.temporaltests.util.dto

data class OrderQuantityAggregate(
    val quantityOrdered: Long,
    val quantityReserved: Long,
    val quantityShipped: Long
) {
    constructor() : this(quantityOrdered = 0L, quantityReserved = 0L, quantityShipped = 0L)

    fun minus(previous: OrderQuantityAggregate): OrderQuantityAggregate =
        OrderQuantityAggregate(
            quantityOrdered = quantityOrdered - previous.quantityOrdered,
            quantityReserved = quantityReserved - previous.quantityReserved,
            quantityShipped = quantityShipped - previous.quantityShipped
        )
}
