package ru.dmkuranov.temporaltests.core.customerorder.db

import org.springframework.data.jpa.repository.JpaRepository
import ru.dmkuranov.temporaltests.core.customerorder.db.CustomerOrderItemEntity

interface CustomerOrderItemRepository : JpaRepository<CustomerOrderItemEntity, Long>
