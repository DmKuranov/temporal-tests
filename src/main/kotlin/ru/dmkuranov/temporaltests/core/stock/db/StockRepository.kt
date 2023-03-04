package ru.dmkuranov.temporaltests.core.stock.db

import org.springframework.data.jpa.repository.JpaRepository
import ru.dmkuranov.temporaltests.core.stock.db.StockEntity

interface StockRepository : JpaRepository<StockEntity, Long> {
    fun findByAvailableIsTrue(): List<StockEntity>
}
