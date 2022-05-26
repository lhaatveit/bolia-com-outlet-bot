package no.haatveit.pricewatcher

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import reactor.core.publisher.Flux
import reactor.util.function.Tuple2

val OBJECT_MAPPER: ObjectMapper = ObjectMapper()
    .registerKotlinModule()
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

operator fun <T1, T2> Tuple2<T1, T2>.component1(): T1 = t1

operator fun <T1, T2> Tuple2<T1, T2>.component2(): T2 = t2

fun <T1, T2> Flux<T1>.withLatestFrom(other: Flux<T2>): Flux<Pair<T1, T2>> {
    return withLatestFrom(other) { t1, t2 -> t1 to t2 }
}
