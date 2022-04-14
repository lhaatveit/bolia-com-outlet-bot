package no.haatveit.bolia

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

val OBJECT_MAPPER = ObjectMapper().registerKotlinModule()

fun encode(str: String) = URLEncoder.encode(str, StandardCharsets.UTF_8)
