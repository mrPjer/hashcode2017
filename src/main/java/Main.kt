data class Endpoint(
        val dataCenterLatency: Int,
        val cacheLatencies: List<Pair<Int, Int>>,
        val expectedVideoRequests: MutableMap<Int, Int>,
        val cacheIds: IntArray
)

data class ExpectedRequest(
        val videoId: Int,
        val endpointId: Int,
        val requestCount: Int
)

data class CacheProposition(
        val cacheId: Int,
        val videoId: Int,
        val savings: Int,
        val originalEndpoint: Int
)

/** The minimal score a cache proposition needs to give to be considered */
val MIN_PROPOSITION_SCORE = 20 * 1000
/** The number of (top) propositions that will be considered */
val PROPOSITIONS_TO_CONSIDER = 2000000

/*
 * |========================================================+===========================|
 * | Problem                | MIN_PROPOSITION_SCORE | PROPOSITIONS_TO_CONSIDER | SCORE  |
 * |------------------------------------------------------------------------------------|
 * | Kittens                | 15000                 | 1000000                  | 516195 |
 * | Trending today         | 20                    | 2000000                  | 352184 |
 * | Me at the zoo          | 10                    | 20000                    | 493023 |
 * | Videos worth spreading | 50                    | 2000000                  | 492769 |
 * |====================================================================================|
 */

fun main(args: Array<String>) {
    val (videoCount, endpointCount, requestDescriptionCount, cacheCount, cacheSize) = readInts()

    val videoSizes = readInts().toTypedArray()

    val expectedRequests = mutableListOf<ExpectedRequest>()

    val cacheSizes = (0..cacheSize - 1).map { cacheSize }.toTypedArray()

    val endpoints = (0..endpointCount - 1).map {
        val (latency, cacheCount) = readInts()
        val cacheIds = mutableListOf<Int>()
        val cacheLatencies = (0..cacheCount - 1).map {
            val (cacheId, latency) = readInts()
            cacheIds.add(cacheId)
            Pair(cacheId, latency)
        }
        Endpoint(latency, cacheLatencies, mutableMapOf(), cacheIds.toIntArray())
    }

    (0..requestDescriptionCount - 1).forEach {
        val (videoId, endpointId, requestCount) = readInts()
        endpoints[endpointId].expectedVideoRequests.put(videoId, requestCount)
        expectedRequests.add(ExpectedRequest(videoId, endpointId, requestCount))
    }


    var requestIndex = 0

    val propositions = expectedRequests.flatMap {
        ++requestIndex

        if (requestIndex % 1000 == 0) {
            log("$requestIndex / ${expectedRequests.size}")
        }

        val endpoint = endpoints[it.endpointId]
        val maxCost = it.requestCount * endpoint.dataCenterLatency

        endpoint.cacheLatencies.map { cache ->
            val cacheId = cache.first
            val latency = cache.second

            val cost = it.requestCount * latency
            val savings = maxCost - cost

            CacheProposition(cacheId, it.videoId, savings, it.endpointId)
        }.filter { it.savings > MIN_PROPOSITION_SCORE }
    }
            .apply { log("${this.size} propositions to consider") }
            .sortedByDescending { it.savings }
            .apply { log("Done sorting") }
            .take(PROPOSITIONS_TO_CONSIDER)
            .apply { log("Took top $PROPOSITIONS_TO_CONSIDER") }
            .toTypedArray()


    var position = 0
    val uselessIndices = (0..propositions.size - 1).map { false }.toBooleanArray()
    val cachePropositions = mutableListOf<CacheProposition>()

    while (position < propositions.size) {
        if (uselessIndices[position]) {
            log("At useless position $position")
            ++position
            continue
        }

        if (LOG) {
            log("Propositions is ${propositions.size - position}")
            //propositions.forEach { log("\t$it") }
        }

        val proposition = propositions[position]

        val remainingSpace = cacheSizes[proposition.cacheId]
        val size = videoSizes[proposition.videoId]

        if (remainingSpace < size) {
            log("Rejecting for size: $proposition")
            ++position
            continue
        }

        cacheSizes[proposition.cacheId] -= size
        cachePropositions.add(proposition)

        log("Accepting $proposition")

        val lessOptimal = propositions.findAll(uselessIndices, position) {
            proposition.originalEndpoint == it.originalEndpoint && it.videoId == proposition.videoId
        }

        lessOptimal.forEach { uselessIndices[it] = true }

        if (LOG) {
            log("Removing less optimal solutions")
            lessOptimal.map { propositions[it] }.forEach { println("\t$it") }
        }

        val endpointMaxCacheSize = endpoints.map { it.cacheIds.map { cacheSizes[it] }.max() ?: 0 }

        val outOfCacheIndices = propositions.findAll(uselessIndices, position) {
            videoSizes[it.videoId] > endpointMaxCacheSize[it.originalEndpoint]
        }

        if (LOG) {
            log("Removing out of cache indices")
            outOfCacheIndices.map { propositions[it] }.forEach { println("\t$it") }
        }

        outOfCacheIndices.forEach { uselessIndices[it] = true }

        ++position
    }


    val cacheServers = mutableMapOf<Int, MutableSet<Int>>()

    cachePropositions.forEach {
        log("Collecting $it")
        if (!cacheServers.containsKey(it.cacheId)) {
            cacheServers.put(it.cacheId, mutableSetOf())
        }
        cacheServers[it.cacheId]!!.add(it.videoId)
    }

    log(cacheServers.toString())

    println(cacheServers.keys.size)
    cacheServers.keys.map { cacheId ->
        cacheServers[cacheId]!!.joinToString(separator = " ", prefix = "$cacheId ")
    }.forEach(::println)
}

private fun <T> Array<T>.findAll(skipArray: BooleanArray, startIndex: Int, predicate: (T) -> Boolean): List<Int> {
    return (startIndex..size - 1)
            .filter { !skipArray[it] }
            .filter { predicate(this[it]) }
}

private fun readInts() = readLine()!!.split(" ").map(Integer::parseInt)

private val LOG = false

private fun log(string: String) {
    if (LOG) {
        println(string)
    }
}
