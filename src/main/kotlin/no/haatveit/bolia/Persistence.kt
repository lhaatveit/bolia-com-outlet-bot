package no.haatveit.bolia

import no.haatveit.bolia.Persistence.CONF_DIRECTORY
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Path
import java.time.Duration
import java.util.function.BiFunction

object Persistence {

    private val HOME_DIRECTORY: Path = Path.of(System.getProperty("user.home"))
    val CONF_DIRECTORY: Path = HOME_DIRECTORY.resolve(".bolia")

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
    cacheKey: String,
    fallbackValue: A,
    accumulator: BiFunction<A, T, A>,
    writeInterval: Duration = Duration.ofSeconds(10)
): Flux<A> {
    return scanPersistent(CONF_DIRECTORY.resolve("$cacheKey.json").toFile(), fallbackValue, accumulator, writeInterval)
}

inline fun <T, reified A> Flux<T>.scanPersistent(
    file: File,
    fallbackValue: A,
    accumulator: BiFunction<A, T, A>,
    writeInterval: Duration
): Flux<A> {

    val accumulatedPublisher = Persistence.load<A>(file)
        .switchIfEmpty(Mono.just(fallbackValue))
        .flatMapMany { initialValue: A ->
            this@scanPersistent.scan(initialValue, accumulator)
        }
        .share()

    // Write to disk every 30 seconds
    val storePublisher = Flux.interval(Duration.ZERO, writeInterval)
        .withLatestFrom(accumulatedPublisher) { _, acc -> acc }
        .distinctUntilChanged()
        .delayUntil { a -> Persistence.store(file, a) }

    return accumulatedPublisher.mergeWith(storePublisher.ignoreElements())
}
