package ru.dmkuranov.temporaltests.core.charge

import org.jetbrains.annotations.TestOnly
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.dmkuranov.temporaltests.core.charge.db.CustomerChargeEntity
import ru.dmkuranov.temporaltests.core.charge.db.CustomerChargeRepository
import ru.dmkuranov.temporaltests.core.charge.dto.ChargeDto
import ru.dmkuranov.temporaltests.core.customerorder.CustomerOrderService
import ru.dmkuranov.temporaltests.core.stock.StockService
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDateTime

@Service
class ChargeService(
    private val customerChargeRepository: CustomerChargeRepository,
    private val stockService: StockService,
    private val customerOrderService: CustomerOrderService,
    private val clock: Clock
) {

    /**
     * @return charge id
     */
    @Transactional
    fun performChargeOnReserve(customerOrderId: Long): Long {
        val order = customerOrderService.loadOrder(customerOrderId)
        val productIdToCharges = order.items.map { orderItem ->
            val stock = stockService.getStock(orderItem.product)
            orderItem.product.productId to stock.price.multiply(BigDecimal(orderItem.quantityReserved))
        }
        if (productIdToCharges.any { it.second <= BigDecimal.ZERO }) {
            throw IllegalArgumentException("Non positive charge for orderId=$customerOrderId")
        }
        val chargeAmount = productIdToCharges.sumOf { it.second }
        return customerChargeRepository.save(
            CustomerChargeEntity(
                orderId = customerOrderId,
                paymentCredential = order.paymentCredential,
                amount = chargeAmount,
                note = "Charge on reservation",
                chargeTime = LocalDateTime.now(clock)
            )
        ).id!!
    }

    @TestOnly
    @Transactional(readOnly = true)
    fun getOrderCharges(customerOrderId: Long): List<ChargeDto> =
        customerChargeRepository.findByOrderId(customerOrderId)
            .map {
                ChargeDto(
                    id = it.id!!,
                    orderId = it.orderId!!,
                    amount = it.amount!!,
                    note = it.note!!
                )
            }
}
