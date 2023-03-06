package ru.dmkuranov.temporaltests.core.charge

import org.springframework.stereotype.Component
import ru.dmkuranov.temporaltests.core.charge.db.CustomerChargeEntity
import ru.dmkuranov.temporaltests.core.charge.dto.ChargeDto

@Component
class ChargeMapper {
    fun map(entity: CustomerChargeEntity) =
        ChargeDto(
            orderId = entity.orderId!!,
            amount = entity.amount!!
        )
}
