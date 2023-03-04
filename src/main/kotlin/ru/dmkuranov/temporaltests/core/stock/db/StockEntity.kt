package ru.dmkuranov.temporaltests.core.stock.db

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType.SEQUENCE
import jakarta.persistence.Id
import jakarta.persistence.SequenceGenerator
import ru.dmkuranov.temporaltests.util.AbstractEntity
import java.math.BigDecimal

@Entity(name = "STOCK")
class StockEntity(
    @Id
    @GeneratedValue(strategy = SEQUENCE, generator = "PRODUCT_SEQ")
    @SequenceGenerator(name = "PRODUCT_SEQ", allocationSize = 1)
    @Column(name = "product_id")
    override var id: Long? = null,

    @Column(nullable = false)
    var quantityStock: Long? = null,

    @Column(nullable = false)
    var quantityReserved: Long? = null,

    @Column(nullable = false)
    var available: Boolean? = null,

    @Column(nullable = false)
    var price: BigDecimal? = null
) : AbstractEntity()
