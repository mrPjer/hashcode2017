data class Endpoint(
        val dataCenterLatency: Int,
        val cacheLatencies: Map<Int, Int>,
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
        }.toMap()
        Endpoint(latency, cacheLatencies, mutableMapOf(), cacheIds.toIntArray())
    }

    (0..requestDescriptionCount - 1).forEach {
        val (videoId, endpointId, requestCount) = readInts()
        endpoints[endpointId].expectedVideoRequests.put(videoId, requestCount)
        expectedRequests.add(ExpectedRequest(videoId, endpointId, requestCount))
    }

    val cachePropositions = mutableListOf<CacheProposition>()

    val propositions = expectedRequests.flatMap {
        val endpoint = endpoints[it.endpointId]
        val maxCost = it.requestCount * endpoint.dataCenterLatency

        endpoint.cacheLatencies.map { cache ->
            val cacheId = cache.key
            val latency = cache.value

            val cost = it.requestCount * latency
            val savings = maxCost - cost

            CacheProposition(cacheId, it.videoId, savings, it.endpointId)
        }
    }
            .filter { it.savings > 1000000 }
            .sortedByDescending { it.savings }
            .take(100000)
            .toTypedArray()


    var position = 0
    val uselessIndices = (0..propositions.size - 1).map { false }.toBooleanArray()

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
