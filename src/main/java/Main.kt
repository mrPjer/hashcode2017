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
        val id: Int,
        val cacheId: Int,
        val videoId: Int,
        val videoSize: Int,
        var totalSavings: Int,
        // (cacheId, videoId) => savings
        val savings: Map<Pair<Int, Int>, Int>,
        val affectedEndpoints: BooleanArray
)

/** The minimal score a cache proposition needs to give to be considered */
val MIN_PROPOSITION_SCORE = 2 * 1000
/** The number of (top) propositions that will be considered */
val PROPOSITIONS_TO_CONSIDER = 10000

val PROPOSITION_SCORE_SIZE_ADJUSTER = 2.0

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

    val cacheVideoPairs: Array<BooleanArray> = Array(cacheCount, { BooleanArray(videoCount) })

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

            (0..cacheCount - 1).filter {
                endpoint.connectedCaches[it]
            }.forEach { cacheId ->
                cacheVideoPairs[cacheId][videoId] = true
            }
        }
    }

    if (LOG) {
        log("Cache video matrix:")
        cacheVideoPairs.forEachIndexed { i, booleans ->
            log("\t$i\t${booleans.joinToString {
                if (it) {
                    "1"
                } else {
                    "0"
                }
            }}")
        }
    }

    var propositionId = 0

    val propositionsToConsider = (0..cacheCount - 1).flatMap { cacheId ->

        (0..videoCount - 1)
                .filter { cacheVideoPairs[cacheId][it] }
                .map { videoId ->

                    log("Assembling $cacheId <= $videoId")

                    val affectedEndpoints = BooleanArray(endpointCount)
                    val videoSize = videoSizes[videoId]

                    val videoSizeScoreAdjuster = (1.0 - (videoSize / cacheSize.toDouble())) pow PROPOSITION_SCORE_SIZE_ADJUSTER

                    val savings = mutableMapOf<Pair<Int, Int>, Int>()
                    val totalSaving = expectedRequests.filter {
                        it.endpoint.connectedCaches[cacheId] && it.videoId == videoId
                    }.map {
                        affectedEndpoints[it.endpoint.id] = true
                        val currentSavings = ((it.requestCount * (it.endpoint.dataCenterLatency - it.endpoint.cacheLatencies[cacheId])) * videoSizeScoreAdjuster).toInt()
                        savings.put(Pair(cacheId, videoId), currentSavings)
                        currentSavings
                    }.sum()

                    Proposition(propositionId++, cacheId, videoId, videoSizes[videoId], totalSaving, savings, affectedEndpoints)
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

        if (shouldSkip[index]) {
            log("Skipping $index / $propositionCount")
            ++index
            continue
        }

        val proposition = propositionsToConsider[index]

        log("Testing $index / $propositionCount - $proposition")

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

        /*
         * Find all propositions that have the same endpoint and video
         * (but different cache) and subtract the points for that endpoint
         * (but leave for the others)
         */
        (index + 1..propositionCount - 1)
                .filter { !shouldSkip[it] }
                .map { propositionsToConsider[it] }
                .filter {
                    it.videoId == proposition.videoId
                            && it.affectedEndpoints[]
                }
                .forEach {
                    val reduction = it.savings[Pair(proposition.cacheId, proposition.videoId)]!!
                    log("\t${it.id} savings reduced from ${it.totalSavings} to ${it.totalSavings - reduction} ($reduction")
                    it.totalSavings -= reduction
                }
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

private infix fun Double.pow(power: Double) = Math.pow(this, power)

private val LOG = true

private fun log(string: String) {
    if (LOG) {
        System.err.println(string)
    }
}
