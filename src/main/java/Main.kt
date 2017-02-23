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
        val savings: Int
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

    val savings = expectedRequests.flatMap {
        val endpoint = endpoints[it.endpointId]
        val maxCost = it.requestCount * endpoint.dataCenterLatency

        endpoint.cacheLatencies.map { cache ->
            val cacheId = cache.key
            val latency = cache.value

            val cost = it.requestCount * latency
            val savings = maxCost - cost

            CacheProposition(cacheId, it.videoId, savings)
        }
    }.sortedByDescending { it.savings }
            .forEach {
                val remainingSpace = cacheSizes[it.cacheId]
                val size = videoSizes[it.videoId]

                if (remainingSpace < size) {
                    return
                }

                cacheSizes[it.cacheId] -= size
                cachePropositions.add(it)
            }

    val cacheServers = cachePropositions.map {
        Pair(it.cacheId, it.videoId)
    }.toMap()

    println(cacheServers.size)
    cacheServers.forEach {
        println()
    }
}

private fun readInts() = readLine()!!.split(" ").map(Integer::parseInt)
