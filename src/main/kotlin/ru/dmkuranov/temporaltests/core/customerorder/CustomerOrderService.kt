package ru.dmkuranov.temporaltests.core.customerorder

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.dmkuranov.temporaltests.core.customerorder.db.CustomerOrderEntity
import ru.dmkuranov.temporaltests.core.customerorder.db.CustomerOrderItemEntity
import ru.dmkuranov.temporaltests.core.customerorder.db.CustomerOrderItemRepository
import ru.dmkuranov.temporaltests.core.customerorder.db.CustomerOrderRepository
import ru.dmkuranov.temporaltests.core.customerorder.dto.CustomerOrderCreateRequestDto
import ru.dmkuranov.temporaltests.core.customerorder.dto.CustomerOrderDto
import java.time.Clock
import java.time.LocalDateTime

@Service
class CustomerOrderService(
    private val customerOrderRepository: CustomerOrderRepository,
    private val customerOrderItemRepository: CustomerOrderItemRepository,
    private val customerOrderMapper: CustomerOrderMapper,
    private val clock: Clock
) {

    @Transactional(readOnly = true)
    fun loadOrder(orderId: Long): CustomerOrderDto =
        customerOrderRepository.findById(orderId)
            .let {
                if (it.isPresent) {
                    return customerOrderMapper.map(it.get())
                } else {
                    throw IllegalArgumentException("Customer order not found for id=$orderId")
                }
            }

    /**
     * @return created order id
     */
    @Transactional
    fun createOrder(request: CustomerOrderCreateRequestDto): Long {
        val orderEntity = CustomerOrderEntity()
        orderEntity.items = request.items.map { requestItem ->
            val orderItemEntity = CustomerOrderItemEntity()
            with(orderItemEntity) {
                order = orderEntity
                productId = requestItem.product.productId
                quantityOrdered = requestItem.quantity
                quantityReserved = 0
                quantityShipped = 0
            }
            orderItemEntity
        }
        orderEntity.paymentCredential = request.paymentCredential
        orderEntity.submittedAt = LocalDateTime.now(clock)
        return customerOrderRepository.save(orderEntity).id!!
    }

    @Transactional
    fun adjustOrderItem(orderItemId: Long, quantityReserved: Long? = null, quantityShipped: Long? = null) {
        customerOrderItemRepository.findById(orderItemId).let {
            if (it.isPresent) {
                val entity = it.get()
                if (quantityReserved != null) {
                    entity.quantityReserved = quantityReserved
                }
                if (quantityShipped != null) {
                    entity.quantityShipped = quantityShipped
                }
                customerOrderItemRepository.save(entity)
            } else {
                throw IllegalArgumentException("Customer order item not found for id=$orderItemId")
            }
        }
    }
}
