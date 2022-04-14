package no.haatveit.bolia

import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Path
import java.time.Duration
import java.util.function.BiFunction

object Persistence {

    private val HOME_DIRECTORY: Path = Path.of(System.getProperty("user.home"))
    val DEFAULT_PATH: Path = HOME_DIRECTORY.resolve(Path.of(".bolia", "state.json"))

    inline fun <reified T> store(file: File, o: T): Mono<Void> = Mono.fromRunnable {
        println("Writing to ${file.toPath().toAbsolutePath()}")
        file.toPath().parent.toFile().mkdirs()
        OBJECT_MAPPER.writeValue(file, o)
    }

    inline fun <reified T> load(file: File): Mono<T> =
        Mono.fromSupplier { OBJECT_MAPPER.readValue(file, T::class.java) }
            .onErrorResume(FileNotFoundException::class.java) { Mono.empty() }
}

inline fun <T, reified A> Flux<T>.scanPersistent(
    file: File,
    fallbackValue: A,
    accumulator: BiFunction<A, T, A>,
    writeInterval: Duration = Duration.ofSeconds(30)
): Flux<A> {

    val accumulatedPublisher = Persistence.load<A>(file)
        .switchIfEmpty(Mono.just(fallbackValue))
        .flatMapMany { initialValue: A ->
            this@scanPersistent.scan(initialValue, accumulator)
        }
        .share()

    val storePublisher = Flux.interval(writeInterval)
        .withLatestFrom(accumulatedPublisher) { _, acc -> acc }
        .delayUntil { a -> Persistence.store(file, a) }

    return accumulatedPublisher.mergeWith(storePublisher.ignoreElements())
}
