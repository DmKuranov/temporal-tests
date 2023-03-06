package ru.dmkuranov.temporaltests.util.processing

class ProcessingException(message: String, cause: Throwable?) :
    RuntimeException(message, cause) {
    constructor(message: String) : this(message, null)
}
