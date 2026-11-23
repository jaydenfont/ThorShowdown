package com.example.thorshowdown.tools

import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.logging.Logger

/** Showdown's move stats (MIT, smogon/pokemon-showdown). */
private const val MOVES_URL = "https://raw.githubusercontent.com/smogon/pokemon-showdown/master/data/moves.ts"

/** Showdown's move descriptions (MIT, smogon/pokemon-showdown). */
private const val MOVES_TEXT_URL = "https://raw.githubusercontent.com/smogon/pokemon-showdown/master/data/text/moves.ts"
private const val HTTP_OK = 200

private val logger: Logger = Logger.getLogger("GenerateMoveData")

/** Downloads Showdown's move data and writes the app's bundled `moves.json` to the path in [args]. */
fun main(args: Array<String>) {
    val output = File(requireNotNull(args.firstOrNull()) { "Usage: GenerateMoveData <output moves.json>" })
    val http = HttpClient.newHttpClient()
    val generated = MoveDataGenerator.generate(http.download(MOVES_URL), http.download(MOVES_TEXT_URL))
    output.parentFile.mkdirs()
    output.writeText(generated)
    logger.info("Wrote ${output.length()} bytes to $output")
}

private fun HttpClient.download(url: String): String {
    val response = send(HttpRequest.newBuilder(URI(url)).build(), HttpResponse.BodyHandlers.ofString())
    check(response.statusCode() == HTTP_OK) { "GET $url returned ${response.statusCode()}" }
    return response.body()
}
