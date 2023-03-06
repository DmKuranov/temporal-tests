package ru.dmkuranov.temporaltests.core.customerorder.db

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import ru.dmkuranov.temporaltests.core.customerorder.db.CustomerOrderEntity

interface CustomerOrderRepository : JpaRepository<CustomerOrderEntity, Long> {
    @Query("select count(1) from CustomerOrderEntity e where e.completedAt is not null")
    fun findCompletedOrderCount(): Long
}
