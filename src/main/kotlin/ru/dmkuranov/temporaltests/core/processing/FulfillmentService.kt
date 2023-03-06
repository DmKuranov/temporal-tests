package ru.dmkuranov.temporaltests.core.processing

import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.dmkuranov.temporaltests.core.charge.dto.ChargeRequestDto
import ru.dmkuranov.temporaltests.core.customerorder.CustomerOrderService
import ru.dmkuranov.temporaltests.core.customerorder.dto.CustomerOrderItemDto
import ru.dmkuranov.temporaltests.core.stock.StockService
import ru.dmkuranov.temporaltests.core.stock.dto.ProductDto
import ru.dmkuranov.temporaltests.core.stock.dto.StockUpdateRequestDto
import java.math.BigDecimal

@Service
class FulfillmentService(
    private val stockService: StockService,
    private val customerOrderService: CustomerOrderService
) {

    /**
     * @returns корректность заказа
     */
    @Transactional
    fun reserveItems(orderId: Long): Boolean =
        customerOrderService.loadOrder(orderId).let {
            return it.items.map { item -> reserveItem(item) }
                .any { reserveItemStatus -> reserveItemStatus }
        }

    /**
     * @returns partial chargeback request if required
     */
    @Transactional
    fun shipItems(orderId: Long): ChargeRequestDto? =
        customerOrderService.loadOrder(orderId).let {
            val chargebackAmount =
                it.items.map { item -> shipItem(item) }
                    .sumOf { itemChargebackAmount -> itemChargebackAmount }

            val result = if (!BigDecimal.ZERO.equals(chargebackAmount))
                ChargeRequestDto(
                    orderId = orderId,
                    amount = chargebackAmount.negate(),
                    note = "chargeback on shipping"
                )
            else null
            log.info { "Shipping order id=$orderId" }
            result
        }

    /**
     * @returns успешность резервирования
     */
    private fun reserveItem(item: CustomerOrderItemDto): Boolean {
        val itemStock = stockService.getStock(item.product)
        val availableForReserve = with(itemStock) { if (quantityAvailable > quantityReserved) quantityAvailable - quantityReserved else 0 }
        val quantityToReserve = if (availableForReserve >= item.quantityOrdered) item.quantityOrdered
        else {
            log.debug { "Adjusting order id=${item.orderId} item [${item.product.productId}] reserve ${item.quantityOrdered}=>$availableForReserve" }
            availableForReserve
        }

        return if (quantityToReserve > 0) {
            val quantityReserved = itemStock.quantityReserved + quantityToReserve
            val stockUpdateRequest = StockUpdateRequestDto(itemStock)
                .copy(
                    quantityReserved = quantityReserved,
                    available = availableForReserve > quantityToReserve
                )
            stockService.updateStock(stockUpdateRequest)
            customerOrderService.adjustOrderItem(item.itemId, quantityReserved = quantityToReserve)
            true
        } else {
            false
        }
    }

    /**
     * @returns сумма к возврату
     */
    private fun shipItem(item: CustomerOrderItemDto): BigDecimal {
        val itemStock = stockService.getStock(item.product)
        val quantityToShip: Long
        val chargebackAmount: BigDecimal
        val stockEmpty: Boolean
        if (itemStock.quantityAvailable >= item.quantityReserved) {
            quantityToShip = item.quantityReserved
            chargebackAmount = BigDecimal.ZERO
            stockEmpty = false
        } else {
            quantityToShip = itemStock.quantityAvailable
            val quantityToChargeback = item.quantityReserved - quantityToShip
            log.warn { "Adjusting order id=${item.orderId} item [${item.product.productId}] shipping ${item.quantityReserved}=>$quantityToShip" }
            chargebackAmount = itemStock.price.multiply(BigDecimal(quantityToChargeback))
            stockEmpty = true
        }
        customerOrderService.adjustOrderItem(item.itemId, quantityShipped = quantityToShip)

        val quantityReserved =
            if (itemStock.quantityReserved < quantityToShip || stockEmpty)
                0L
            else
                itemStock.quantityReserved - quantityToShip

        val stockUpdateRequest = StockUpdateRequestDto(item.product).copy(
            quantityAvailable = itemStock.quantityAvailable - quantityToShip,
            quantityReserved = quantityReserved,
            quantityShipped = itemStock.quantityShipped + quantityToShip
        )
        stockService.updateStock(stockUpdateRequest)
        return chargebackAmount
    }

    companion object {
        private val log = KotlinLogging.logger {}
    }
}
