package ru.dmkuranov.temporaltests.core.customerorder.db

import org.springframework.data.jpa.repository.JpaRepository
import ru.dmkuranov.temporaltests.core.customerorder.db.CustomerOrderEntity

interface CustomerOrderRepository : JpaRepository<CustomerOrderEntity, Long>

