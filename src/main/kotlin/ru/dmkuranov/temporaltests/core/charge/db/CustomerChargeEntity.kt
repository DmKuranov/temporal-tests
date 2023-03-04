package ru.dmkuranov.temporaltests.core.charge.db

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.SequenceGenerator
import ru.dmkuranov.temporaltests.util.AbstractEntity
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity(name = "CUSTOMER_CHARGE")
class CustomerChargeEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "CUSTOMER_CHARGE_SEQ")
    @SequenceGenerator(name = "CUSTOMER_CHARGE_SEQ", allocationSize = 1)
    override var id: Long? = null,

    @Column(nullable = false)
    var orderId: Long? = null,

    @Column(nullable = false)
    var paymentCredential: String? = null,

    @Column(nullable = false)
    var amount: BigDecimal? = null,

    @Column(nullable = false)
    var note: String? = null,

    @Column(nullable = false)
    var chargeTime: LocalDateTime? = null
) : AbstractEntity()
