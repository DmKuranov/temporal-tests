package ru.dmkuranov.temporaltests.core.charge

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.dmkuranov.temporaltests.core.charge.db.CustomerChargeEntity
import ru.dmkuranov.temporaltests.core.charge.db.CustomerChargeRepository
import ru.dmkuranov.temporaltests.core.charge.dto.ChargeDto
import ru.dmkuranov.temporaltests.core.charge.dto.ChargeRequestDto
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
    private val chargeMapper: ChargeMapper,
    private val clock: Clock
) {

    @Transactional(readOnly = true)
    fun getCharges(customerOrderId: Long): List<ChargeDto> =
        customerChargeRepository.findByOrderId(customerOrderId)
            .map { chargeMapper.map(it) }

    @Transactional
    fun performChargeOnReserve(customerOrderId: Long): ChargeDto {
        val order = customerOrderService.loadOrder(customerOrderId)
        val productIdToCharges = order.items.map { orderItem ->
            val stock = stockService.getStock(orderItem.product)
            orderItem.product.productId to stock.price.multiply(BigDecimal(orderItem.quantityReserved))
        }
        val chargeAmount = productIdToCharges.sumOf { it.second }
        val chargeEntity = customerChargeRepository.save(
            CustomerChargeEntity(
                orderId = customerOrderId,
                paymentCredential = order.paymentCredential,
                amount = chargeAmount,
                note = "Charge on reservation",
                chargeTime = LocalDateTime.now(clock)
            )
        )
        Thread.sleep(CHARGE_SLEEP_DURATION_MS)
        return chargeMapper.map(chargeEntity)
    }

    @Transactional
    fun performCharge(chargeRequest: ChargeRequestDto): ChargeDto =
        performChargeInternal(chargeRequest)

    /**
     * non-failing method
     */
    @Transactional
    fun performChargeSafe(chargeRequest: ChargeRequestDto) =
        performChargeInternal(chargeRequest)

    private fun performChargeInternal(chargeRequest: ChargeRequestDto): ChargeDto =
        customerOrderService.loadOrder(chargeRequest.orderId)
            .let { order ->
                customerChargeRepository.save(
                    CustomerChargeEntity(
                        orderId = chargeRequest.orderId,
                        paymentCredential = order.paymentCredential,
                        amount = chargeRequest.amount,
                        note = chargeRequest.note,
                        chargeTime = LocalDateTime.now(clock)
                    )
                )
            }
            .let { chargeMapper.map(it) }

    companion object {
        const val CHARGE_SLEEP_DURATION_MS = 50L
    }
}
