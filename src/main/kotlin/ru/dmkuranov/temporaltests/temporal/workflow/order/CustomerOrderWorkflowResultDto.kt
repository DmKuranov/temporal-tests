package ru.dmkuranov.temporaltests.temporal.workflow.order

data class CustomerOrderWorkflowResultDto(
    val orderId: Long,
    val valid: Boolean? = null,
    val failed: Boolean = false
)
