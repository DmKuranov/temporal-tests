package ru.dmkuranov.temporaltests.util.invocation

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class TransactionalInvoker<T> : Invoker<T> {

    @Transactional
    override fun <R : T> invoke(func: () -> R): R {
        return func()
    }
}
