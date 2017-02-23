data class Endpoint(
        val dataCenterLatency: Int,
        val cacheLatencies: Map<Int, Int>,
        val expectedVideoRequests: MutableMap<Int, Int>
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

    val videoSizes = readInts()

    val expectedRequests = mutableListOf<ExpectedRequest>()

    val cacheSizes = (0..cacheSize - 1).map { cacheSize }.toMutableList()

    val endpoints = (0..endpointCount - 1).map {
        val (latency, cacheCount) = readInts()
        val cacheLatencies = (0..cacheCount - 1).map {
            val (cacheId, latency) = readInts()
            Pair(cacheId, latency)
        }.toMap()
        Endpoint(latency, cacheLatencies, mutableMapOf())
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
    }.sortedByDescending { it.savings }.toMutableList()

    while (!propositions.isEmpty()) {
        if (LOG) {
            log("Propositions is ${propositions.size}")
            //propositions.forEach { log("\t$it") }
        }
        val proposition = propositions.first()
        propositions.remove(proposition)

        val remainingSpace = cacheSizes[proposition.cacheId]
        val size = videoSizes[proposition.videoId]

        if (remainingSpace < size) {
            log("Rejecting for size: $proposition")
            continue
        }

        cacheSizes[proposition.cacheId] -= size
        cachePropositions.add(proposition)

        log("Accepting $proposition")

        propositions.removeAll {
            proposition.originalEndpoint == it.originalEndpoint && it.videoId == proposition.videoId
        }

        val minCacheSize = cacheSizes.min()!!

        propositions.removeAll { videoSizes[it.videoId] > minCacheSize }
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

private fun readInts() = readLine()!!.split(" ").map(Integer::parseInt)

private val LOG = false

private fun log(string: String) {
    if (LOG) {
        println(string)
    }
}
