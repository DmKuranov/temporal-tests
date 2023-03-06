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
import jakarta.persistence.Table
import ru.dmkuranov.temporaltests.util.AbstractEntity
import java.time.LocalDateTime

@Entity
@Table(name = "CUSTOMER_ORDER_ITEM")
class CustomerOrderItemEntity : AbstractEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "CUSTOMER_ORDER_ITEM_SEQ")
    @SequenceGenerator(name = "CUSTOMER_ORDER_ITEM_SEQ", allocationSize = 1)
    override var id: Long? = null

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", updatable = false)
    var order: CustomerOrderEntity? = null

    @Column(nullable = false, updatable = false)
    var orderSubmittedAt: LocalDateTime? = null

    @Column(nullable = false, updatable = false)
    var productId: Long? = null

    @Column(nullable = false, updatable = false)
    var quantityOrdered: Long? = null

    @Column(nullable = false)
    var quantityReserved: Long? = null

    @Column(nullable = false)
    var quantityShipped: Long? = null
}
