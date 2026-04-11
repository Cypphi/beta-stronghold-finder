package dev.cypphi.betastrongholdfinder

import java.util.Random
import kotlin.math.cos
import kotlin.math.sin
import kotlin.system.exitProcess

data class ChunkPos(val x: Int, val z: Int) {
    val blockX: Int
        get() = (x shl 4) + 8

    val blockZ: Int
        get() = (z shl 4) + 8

    val regionX: Int
        get() = x shr 5

    val regionZ: Int
        get() = z shr 5

    val regionFile: String
        get() = "region/r.$regionX.$regionZ.mcr"
}

private object StrongholdFinder {
    private const val STRONGHOLD_COUNT = 3

    fun find(seed: Long): List<ChunkPos> {
        val random = Random(seed)
        val positions = ArrayList<ChunkPos>(STRONGHOLD_COUNT)
        var angle = random.nextDouble() * Math.PI * 2.0

        repeat(STRONGHOLD_COUNT) {
            val distance = (1.25 + random.nextDouble()) * 32.0
            var chunkX = Math.round(cos(angle) * distance).toInt()
            var chunkZ = Math.round(sin(angle) * distance).toInt()

            val biomeAdjustedChunk =
                StrongholdBiomeSearch.findNearestAllowedStrongholdBiome(seed, chunkX, chunkZ, random)

            if (biomeAdjustedChunk != null) {
                chunkX = biomeAdjustedChunk.x
                chunkZ = biomeAdjustedChunk.z
            } else {
                println("Placed stronghold in INVALID biome at ($chunkX,$chunkZ)")
            }

            positions += ChunkPos(chunkX, chunkZ)
            angle += (Math.PI * 2.0) / STRONGHOLD_COUNT.toDouble()
        }

        // This overwrite is present in `ul.a(int, int)` in b1.9-pre3-1350.jar.
        positions[0] = ChunkPos(0, 0)
        return positions
    }
}

fun main(args: Array<String>) {
    if (args.singleOrNull() == "-h" || args.singleOrNull() == "--help") {
        printUsage()
        return
    }

    val seed = parseSeed(args) ?: promptForSeed()
    if (seed == null) {
        printUsage()
        exitProcess(1)
    }

    StrongholdFinder.find(seed).forEachIndexed { index, position ->
        println(
            "stronghold ${index + 1}: chunk=(${position.x}, ${position.z}) block=(${position.blockX}, ${position.blockZ}) region=(${position.regionX}, ${position.regionZ}) file=${position.regionFile}",
        )
    }
}

private fun parseSeed(args: Array<String>): Long? {
    if (args.isEmpty()) {
        return null
    }

    return when (val argument = args[0]) {
        "-seed", "--seed" -> args.getOrNull(1)?.toLongOrNull()
        else -> argument.toLongOrNull().also {
            if (it == null) {
                System.err.println("Unknown argument: $argument")
            }
        }
    }
}

private fun promptForSeed(): Long? {
    print("Enter world seed: ")
    return readlnOrNull()?.trim()?.takeIf { it.isNotEmpty() }?.toLongOrNull().also {
        if (it == null) {
            System.err.println("Expected a valid numeric world seed.")
        }
    }
}

private fun printUsage() {
    println("Usage: java -jar beta-stronghold-finder.jar [-seed] <world-seed>")
    println("   or: java -jar beta-stronghold-finder.jar")
}
