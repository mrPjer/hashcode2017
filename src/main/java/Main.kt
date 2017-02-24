data class Endpoint(
        val id: Int,
        val dataCenterLatency: Int,
        val cacheLatencies: IntArray,
        val connectedCaches: BooleanArray
)

data class ExpectedRequest(
        val videoId: Int,
        val endpoint: Endpoint,
        val requestCount: Int
)

data class Proposition(
        val cacheId: Int,
        val videoId: Int,
        val videoSize: Int,
        val totalSavings: Int,
        val affectedEndpoints: BooleanArray
)

/** The minimal score a cache proposition needs to give to be considered */
val MIN_PROPOSITION_SCORE = 0 * 1000
/** The number of (top) propositions that will be considered */
val PROPOSITIONS_TO_CONSIDER = Int.MAX_VALUE

/*
 * |========================================================+===========================|
 * | Problem                | MIN_PROPOSITION_SCORE | PROPOSITIONS_TO_CONSIDER | SCORE  |
 * |------------------------------------------------------------------------------------|
 * | Kittens                | 15000                 | 1000000                  | 516195 |
 * | Trending today         | 20ni                     | 2000000                  | 352184 |
 * | Me at the zoo          | 10                    | 20000                    | 493023 |
 * | Videos worth spreading | 50                    | 2000000                  | 492769 |
 * |====================================================================================|
 */

fun main(args: Array<String>) {
    val (videoCount, endpointCount, requestDescriptionCount, cacheCount, cacheSize) = readInts()

    log("$videoCount videos")
    log("$endpointCount endpoints")
    log("$cacheCount caches")
    log("$cacheSize cache size")

    val videoSizes = readInts().toTypedArray()

    log("Video sizes: ${videoSizes.joinToString(" ")}")

    val expectedRequests = mutableListOf<ExpectedRequest>()

    val cacheSizes = (0..cacheSize - 1).map { cacheSize }.toTypedArray()

    val endpoints = (0..endpointCount - 1).map {
        log("Reading endpoint $it")
        val (endpointLatency, endpointCacheCount) = readInts()
        log("\tlatency = $endpointLatency, cacheCount = $cacheCount")
        val connectedCaches = BooleanArray(cacheCount, init = { false })
        val cacheLatencies = IntArray(cacheCount, init = { Int.MAX_VALUE })
        (0..endpointCacheCount - 1).forEach {
            val (cacheId, latency) = readInts()
            cacheLatencies[cacheId] = latency
            connectedCaches[cacheId] = true
        }
        Endpoint(it, endpointLatency, cacheLatencies, connectedCaches)
    }

    (0..requestDescriptionCount - 1).forEach {
        val (videoId, endpointId, requestCount) = readInts()
        val endpoint = endpoints[endpointId]

        if (endpoint.connectedCaches.any()) {
            expectedRequests.add(ExpectedRequest(videoId, endpoints[endpointId], requestCount))
        }
    }

    val propositionsToConsider = (0..cacheCount - 1).flatMap { cacheId ->

        (0..videoCount - 1).map { videoId ->

            val affectedEndpoints = BooleanArray(endpointCount)

            val totalSaving = expectedRequests.filter {
                it.endpoint.connectedCaches[cacheId] && it.videoId == videoId
            }.map {
                affectedEndpoints[it.endpoint.id] = true
                it.requestCount * (it.endpoint.dataCenterLatency - it.endpoint.cacheLatencies[cacheId])
            }.sum()

            Proposition(cacheId, videoId, videoSizes[videoId], totalSaving, affectedEndpoints)
        }.filter {
            it.totalSavings >= MIN_PROPOSITION_SCORE
        }

    }
            .sortedByDescending { it.totalSavings }
            .take(PROPOSITIONS_TO_CONSIDER)
            .toTypedArray()

    if (LOG) {
        log("Propositions to consider:")
        propositionsToConsider.forEach {
            log("\t${it.cacheId} <= ${it.videoId} = ${it.totalSavings}")
        }
    }

    val propositionCount = propositionsToConsider.size
    val shouldSkip = BooleanArray(propositionCount)
    var index = 0

    val acceptedPropositions = mutableSetOf<Proposition>()

    while (index < propositionCount) {
        log("Testing $index / $propositionCount")

        if (shouldSkip[index]) {
            log("\tSkipping")
            ++index
            continue
        }

        val proposition = propositionsToConsider[index]

        if (proposition.videoSize > cacheSizes[proposition.cacheId]) {
            log("\tDropping for size: $proposition")
            ++index
            continue
        }

        log("\tAccepting")

        cacheSizes[proposition.cacheId] -= proposition.videoSize
        acceptedPropositions.add(proposition)

        /*
         * Prune less optimal propositions
         *
         * Find all propositions where the cache and the video are the
         * same and the connected endpoints are a subset of the current
         * connected endpoint.
         *
         * ==> This will never happen
         *
         */

        /*
         * Find all propositions that have the same endpoint and video
         * (but different cache) and subtract the points for that endpoint
         * (but leave for the others)
         */

        /*
         * Prune propositions that no longer fit
         *
         * Find all propositions that have the video size larger than the
         * max available cache.
         */

        (index + 1..propositionCount - 1)
                .filter { !shouldSkip[it] }
                .filter {
                    val proposition = propositionsToConsider[it]
                    proposition.videoSize > cacheSizes[proposition.cacheId]
                }
                .forEach { shouldSkip[it] = true }

        ++index
    }


    val cacheServers = mutableMapOf<Int, MutableSet<Int>>()

    acceptedPropositions.forEach {
        val existing = cacheServers.getOrPut(it.cacheId, { mutableSetOf() })
        existing.add(it.videoId)
    }

    log(cacheServers.toString())

    println(cacheServers.keys.size)
    cacheServers.keys.map { cacheId ->
        cacheServers[cacheId]!!.joinToString(separator = " ", prefix = "$cacheId ")
    }.forEach(::println)
}

private fun readInts() = readLine()!!.split(" ").map(Integer::parseInt)

private val LOG = true

private fun log(string: String) {
    if (LOG) {
        println(string)
    }
}
