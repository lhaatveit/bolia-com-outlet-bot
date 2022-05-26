package no.haatveit.pricewatcher

import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import java.io.File

class PersistenceTest {

    private fun test(
        initial: Set<Int>,
        input: List<Int>,
        output: List<Int>,
        file: File = File.createTempFile("test", ".json")
    ) {
        val persistent = Persistent.ObjectMapperPersistent(file, initial, Set::class.java)
        val distinctFlux = Flux.fromIterable(input)
            .doOnNext { println("Next first is $it") }
            .distinctPersistent(persistent, keySelector = { k -> k })

        StepVerifier.create(distinctFlux)
            .expectNext(*output.toTypedArray())
            .expectComplete()
            .verify()
    }

    @Test
    fun testDistinctPersistent() {

        test(
            initial = setOf(),
            input = listOf(1, 1),
            output = listOf(1)
        )

        test(
            initial = setOf(1),
            input = listOf(1, 2, 3, 3, 5, 6, 6, 6, 9, 10, 10),
            output = listOf(2, 3, 5, 6, 9, 10)
        )

        test(
            initial = setOf(),
            input = listOf(1),
            output = listOf(1)
        )

        test(
            initial = setOf(2),
            input = listOf(1),
            output = listOf(1)
        )

        test(
            initial = setOf(1),
            input = listOf(1, 1, 1, 1, 1, 1, 1),
            output = listOf()
        )

        test(
            initial = setOf(),
            input = listOf(),
            output = listOf()
        )

        test(
            initial = setOf(),
            input = listOf(1, 2, 3, 2, 1),
            output = listOf(1, 2, 3)
        )

        val file = File.createTempFile("test", ".json")

        test(
            initial = setOf(),
            input = listOf(1, 2, 3),
            output = listOf(1, 2, 3),
            file = file
        )

        test(
            initial = setOf(),
            input = listOf(1, 2, 3),
            output = listOf(),
            file = file
        )
    }
}
