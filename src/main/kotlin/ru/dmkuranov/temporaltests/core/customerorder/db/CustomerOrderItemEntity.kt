package ru.dmkuranov.temporaltests.core.customerorder.db

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.SequenceGenerator
import ru.dmkuranov.temporaltests.util.AbstractEntity

@Entity(name = "CUSTOMER_ORDER_ITEM")
class CustomerOrderItemEntity : AbstractEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "CUSTOMER_ORDER_ITEM_SEQ")
    @SequenceGenerator(name = "CUSTOMER_ORDER_ITEM_SEQ", allocationSize = 1)
    override var id: Long? = null

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    var order: CustomerOrderEntity? = null

    @Column(nullable = false)
    var productId: Long? = null

    @Column(nullable = false)
    var quantityOrdered: Long? = null

    @Column(nullable = false)
    var quantityReserved: Long? = null

    @Column(nullable = false)
    var quantityShipped: Long? = null
}
