data class Endpoint(
        val dataCenterLatency: Int,
        val cacheLatencies: Map<Int, Int>,
        val expectedVideoRequests: MutableMap<Int, Int>
)

fun main(args: Array<String>) {
    val (videoCount, endpointCount, requestDescriptionCount, cacheCount, cacheSize) = readInts()

    val videoSizes = readInts()

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
    }

    endpoints.forEach { println(it) }
}

private fun readInts() = readLine()!!.split(" ").map(Integer::parseInt)
