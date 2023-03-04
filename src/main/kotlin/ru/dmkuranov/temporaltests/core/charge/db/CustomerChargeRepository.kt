package ru.dmkuranov.temporaltests.core.charge.db

import org.jetbrains.annotations.TestOnly
import org.springframework.data.jpa.repository.JpaRepository
import ru.dmkuranov.temporaltests.core.charge.db.CustomerChargeEntity

interface CustomerChargeRepository : JpaRepository<CustomerChargeEntity, Long> {

    @TestOnly
    fun findByOrderId(orderId: Long): List<CustomerChargeEntity>
}
