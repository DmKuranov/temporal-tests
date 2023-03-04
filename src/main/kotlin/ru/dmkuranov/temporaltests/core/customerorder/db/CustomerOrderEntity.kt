package ru.dmkuranov.temporaltests.core.customerorder.db

import jakarta.persistence.CascadeType.ALL
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.OrderBy
import jakarta.persistence.SequenceGenerator
import ru.dmkuranov.temporaltests.util.AbstractEntity
import java.time.LocalDateTime

@Entity(name = "CUSTOMER_ORDER")
class CustomerOrderEntity : AbstractEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "CUSTOMER_ORDER_SEQ")
    @SequenceGenerator(name = "CUSTOMER_ORDER_SEQ", allocationSize = 1)
    override var id: Long? = null

    @OneToMany(mappedBy = "order", cascade = [ALL], fetch = FetchType.LAZY)
    @OrderBy("id")
    var items: List<CustomerOrderItemEntity>? = null

    @Column
    var paymentCredential: String? = null

    @Column(nullable = false)
    var submittedAt: LocalDateTime? = null
}
