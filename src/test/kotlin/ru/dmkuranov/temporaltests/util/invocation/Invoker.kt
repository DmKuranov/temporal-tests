package ru.dmkuranov.temporaltests.util.invocation

import io.grpc.StatusRuntimeException
import org.springframework.dao.CannotAcquireLockException
import org.springframework.orm.ObjectOptimisticLockingFailureException
import ru.dmkuranov.temporaltests.util.processing.ProcessingException
import kotlin.math.pow

interface Invoker<T> {
    fun <R : T> invoke(func: () -> R): R

    class ExceptionRetryingInvoker(
        val initialIntervalMs: Int,
        val backoffCoefficient: Double,
        val maxIntervalMs: Long,
        val exceptionClasses: List<Class<*>>
    ) : Invoker<Any?> {
        private val swallower = ExceptionSwallower(exceptionClasses = exceptionClasses)

        override fun <R> invoke(func: () -> R) =
            invokeWithRetryConditional(
                initialIntervalMs = initialIntervalMs,
                backoffCoefficient = backoffCoefficient,
                maxIntervalMs = maxIntervalMs,
                exceptionSwallower = swallower::swallow,
                invocation = func
            )
    }

    class WhileListNotEmptyInvoker<I>(
        val initialIntervalMs: Int,
        val backoffCoefficient: Double,
        val maxIntervalMs: Long,
    ) : Invoker<List<I>> {
        override fun <R : List<I>> invoke(func: () -> R) =
            invokeWithRetryConditional(
                initialIntervalMs = initialIntervalMs,
                backoffCoefficient = backoffCoefficient,
                maxIntervalMs = maxIntervalMs,
                condition = { it?.isNotEmpty() ?: false },
                invocation = func
            )
    }

    class PlainInvoker<T> : Invoker<T> {
        override fun <R : T> invoke(func: () -> R) = func()
    }

    companion object {
        val plain = PlainInvoker<Any?>()

        val dbConcurrencyRetry = ExceptionRetryingInvoker(
            initialIntervalMs = 50,
            backoffCoefficient = 1.2,
            maxIntervalMs = 5000,
            exceptionClasses = listOf(ObjectOptimisticLockingFailureException::class.java, CannotAcquireLockException::class.java)
        )

        val temporalOpenToClosedTransitionRetry = object : Invoker<Any?> {
            override fun <R> invoke(func: () -> R) =
                invokeWithRetryConditional(
                    initialIntervalMs = 500,
                    backoffCoefficient = 1.2,
                    maxIntervalMs = 10000,
                    exceptionSwallower = { exception ->
                        if (exception is StatusRuntimeException && (
                                exception.message!!.startsWith("FAILED_PRECONDITION: Workflow state is not ready to handle the request") ||
                                    exception.message!!.startsWith("NOT_FOUND: Workflow executionsRow not found")
                                )
                        ) Unit
                        else throw exception
                    },
                    invocation = func
                )
        }

        @Suppress("LongParameterList")
        fun <R> invokeWithRetryConditional(
            initialIntervalMs: Int,
            backoffCoefficient: Double,
            maxIntervalMs: Long,
            condition: (R?) -> Boolean = { _ -> true },
            exceptionSwallower: (Exception) -> Unit = { exception -> throw exception },
            invocation: () -> R
        ): R {
            var retryNumber = 0
            var lastCaughtException: Exception? = null
            while (true) {
                try {
                    val result = invocation()
                    if (condition(result)) {
                        return result
                    }
                } catch (e: Exception) {
                    exceptionSwallower(e)
                    lastCaughtException = e
                }
                val interval = (initialIntervalMs * backoffCoefficient.pow(retryNumber.toDouble())).toLong()
                if (interval > maxIntervalMs) {
                    break
                } else {
                    retryNumber++
                    Thread.sleep(interval)
                }
            }
            throw ProcessingException("Max retry interval ${maxIntervalMs}ms reached", lastCaughtException)
        }
    }

    private class ExceptionSwallower(
        val exceptionClasses: List<Class<*>>
    ) {
        fun swallow(e: Exception) =
            if (exceptionClasses.none { e.javaClass.isAssignableFrom(it) }) throw e
            else Unit
    }
}
