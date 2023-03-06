package ru.dmkuranov.temporaltests.util.supplier

import kotlin.random.Random

class RandomEntityPoolSupplier<T>(
    private val entities: List<T>
) {

    fun next(
        count: Int,
        availabilityFilter: (List<T>) -> List<T>
    ): List<T> =
        availabilityFilter(entities)
            .let { filtered ->
                val countEffective = if (count > filtered.size) filtered.size else count
                (0..countEffective).asSequence()
                    .map { filtered[Random.nextInt(filtered.size)] }
                    .toList()
            }
}
