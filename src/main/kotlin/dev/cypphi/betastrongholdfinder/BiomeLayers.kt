package dev.cypphi.betastrongholdfinder

import java.util.Random

private object Beta119Biomes {
    const val OCEAN = 0
    const val PLAINS = 1
    const val DESERT = 2
    const val EXTREME_HILLS = 3
    const val FOREST = 4
    const val TAIGA = 5
    const val SWAMPLAND = 6
    const val RIVER = 7
    const val FROZEN_OCEAN = 10
    const val FROZEN_RIVER = 11
    const val ICE_PLAINS = 12
    const val MUSHROOM_ISLAND = 14
    const val MUSHROOM_ISLAND_SHORE = 15

    private fun climate(value: Float): Int = (value * 65536.0f).toInt()

    private val temperatures = intArrayOf(
        climate(0.5f),
        climate(0.8f),
        climate(2.0f),
        climate(0.2f),
        climate(0.7f),
        climate(0.3f),
        climate(0.8f),
        climate(0.5f),
        climate(2.0f),
        climate(0.5f),
        climate(0.0f),
        climate(0.0f),
        climate(0.0f),
        climate(0.0f),
        climate(0.9f),
        climate(0.9f),
    )

    private val rainfalls = intArrayOf(
        climate(0.5f),
        climate(0.4f),
        climate(0.0f),
        climate(0.3f),
        climate(0.8f),
        climate(0.8f),
        climate(0.9f),
        climate(0.5f),
        climate(0.0f),
        climate(0.5f),
        climate(0.5f),
        climate(0.5f),
        climate(0.5f),
        climate(0.5f),
        climate(1.0f),
        climate(1.0f),
    )

    val strongholdBiomes = setOf(DESERT, FOREST, EXTREME_HILLS, SWAMPLAND)

    fun temperature(id: Int): Int = temperatures[id]

    fun rainfall(id: Int): Int = rainfalls[id]
}

object StrongholdBiomeSearch {
    private val layers = HashMap<Long, GenLayer>()

    fun findNearestAllowedStrongholdBiome(seed: Long, chunkX: Int, chunkZ: Int, random: Random): ChunkPos? {
        val biomeLayer = layers.getOrPut(seed) { buildBiomeLayer(seed) }
        val centerX = (chunkX shl 4) + 8
        val centerZ = (chunkZ shl 4) + 8
        val minX = (centerX - 112) shr 2
        val minZ = (centerZ - 112) shr 2
        val maxX = (centerX + 112) shr 2
        val maxZ = (centerZ + 112) shr 2
        val width = maxX - minX + 1
        val height = maxZ - minZ + 1
        val biomes = biomeLayer.getInts(minX, minZ, width, height)

        var chosenX = 0
        var chosenZ = 0
        var found = false
        var count = 0

        for (index in biomes.indices) {
            val sampleX = (minX + index % width) shl 2
            val sampleZ = (minZ + index / width) shl 2
            val biome = biomes[index]

            if (biome in Beta119Biomes.strongholdBiomes) {
                if (!found || random.nextInt(count + 1) == 0) {
                    chosenX = sampleX
                    chosenZ = sampleZ
                    found = true
                    count += 1
                }
            }
        }

        if (!found) {
            return null
        }

        return ChunkPos(chosenX shr 4, chosenZ shr 4)
    }

    private fun buildBiomeLayer(seed: Long): GenLayer {
        var land: GenLayer = IslandLayer(1L)
        land = FuzzyZoomLayer(2000L, land)
        land = AddIslandLayer(1L, land)
        land = ZoomLayer(2001L, land)
        land = AddIslandLayer(2L, land)
        land = AddSnowLayer(2L, land)
        land = ZoomLayer(2002L, land)
        land = AddIslandLayer(3L, land)
        land = ZoomLayer(2003L, land)
        land = AddIslandLayer(4L, land)
        land = AddMushroomIslandLayer(5L, land)

        var river: GenLayer = RiverInitLayer(100L, land)
        river = magnify(1000L, river, 6)
        river = RiverLayer(1L, river)
        river = SmoothLayer(1000L, river)

        var biomes: GenLayer = BiomeLayer(200L, land)
        biomes = magnify(1000L, biomes, 2)

        repeat(4) { index ->
            biomes = ZoomLayer(1000L + index, biomes)
            if (index == 0) {
                biomes = AddIslandLayer(3L, biomes)
                biomes = MushroomShoreLayer(1000L, biomes)
            }
        }

        biomes = SmoothLayer(1000L, biomes)

        return RiverMixLayer(100L, biomes, river).also { it.initWorldGenSeed(seed) }
    }

    private fun magnify(seed: Long, layer: GenLayer, count: Int): GenLayer {
        var current = layer
        repeat(count) { index ->
            current = ZoomLayer(seed + index, current)
        }
        return current
    }
}

private abstract class GenLayer(seed: Long) {
    protected open var parent: GenLayer? = null

    private val baseSeed: Long
    private var worldGenSeed = 0L
    private var chunkSeed = 0L

    init {
        var current = seed
        current = mixSeed(current, seed)
        current = mixSeed(current, seed)
        current = mixSeed(current, seed)
        baseSeed = current
    }

    open fun initWorldGenSeed(seed: Long) {
        parent?.initWorldGenSeed(seed)
        var current = seed
        current = mixSeed(current, baseSeed)
        current = mixSeed(current, baseSeed)
        current = mixSeed(current, baseSeed)
        worldGenSeed = current
    }

    protected fun initChunkSeed(x: Long, z: Long) {
        var current = worldGenSeed
        current = mixSeed(current, x)
        current = mixSeed(current, z)
        current = mixSeed(current, x)
        current = mixSeed(current, z)
        chunkSeed = current
    }

    protected fun nextInt(bound: Int): Int {
        var value = ((chunkSeed shr 24) % bound.toLong()).toInt()
        if (value < 0) {
            value += bound
        }
        chunkSeed = mixSeed(chunkSeed, worldGenSeed)
        return value
    }

    protected fun choose(first: Int, second: Int): Int = if (nextInt(2) == 0) first else second

    protected fun chooseRandom(first: Int, second: Int, third: Int, fourth: Int): Int =
        when (nextInt(4)) {
            0 -> first
            1 -> second
            2 -> third
            else -> fourth
        }

    abstract fun getInts(x: Int, z: Int, width: Int, height: Int): IntArray

    private fun mixSeed(seed: Long, value: Long): Long =
        seed * (seed * 6364136223846793005L + 1442695040888963407L) + value
}

private class IslandLayer(seed: Long) : GenLayer(seed) {
    override fun getInts(x: Int, z: Int, width: Int, height: Int): IntArray {
        val values = IntArray(width * height)

        for (row in 0 until height) {
            for (column in 0 until width) {
                initChunkSeed((x + column).toLong(), (z + row).toLong())
                values[column + row * width] = if (nextInt(10) == 0) 1 else 0
            }
        }

        if (x > -width && x <= 0 && z > -height && z <= 0) {
            values[-x + -z * width] = 1
        }

        return values
    }
}

private open class ZoomLayer(seed: Long, parentLayer: GenLayer) : GenLayer(seed) {
    init {
        parent = parentLayer
    }

    override fun getInts(x: Int, z: Int, width: Int, height: Int): IntArray {
        val parentX = x shr 1
        val parentZ = z shr 1
        val parentWidth = (width shr 1) + 3
        val parentHeight = (height shr 1) + 3
        val parentValues = parent!!.getInts(parentX, parentZ, parentWidth, parentHeight)
        val zoomedWidth = parentWidth shl 1
        val zoomedHeight = parentHeight shl 1
        val zoomed = IntArray(zoomedWidth * zoomedHeight)

        for (parentRow in 0 until parentHeight - 1) {
            var destIndex = (parentRow shl 1) * zoomedWidth
            var northWest = parentValues[parentRow * parentWidth]
            var southWest = parentValues[(parentRow + 1) * parentWidth]

            for (parentColumn in 0 until parentWidth - 1) {
                initChunkSeed(((parentColumn + parentX) shl 1).toLong(), ((parentRow + parentZ) shl 1).toLong())

                val northEast = parentValues[parentColumn + 1 + parentRow * parentWidth]
                val southEast = parentValues[parentColumn + 1 + (parentRow + 1) * parentWidth]

                zoomed[destIndex] = northWest
                zoomed[destIndex + zoomedWidth] = choose(northWest, southWest)
                zoomed[destIndex + 1] = choose(northWest, northEast)
                zoomed[destIndex + 1 + zoomedWidth] = chooseModeOrRandom(northWest, northEast, southWest, southEast)

                destIndex += 2
                northWest = northEast
                southWest = southEast
            }
        }

        val result = IntArray(width * height)
        for (row in 0 until height) {
            val sourceIndex = (row + (z and 1)) * zoomedWidth + (x and 1)
            zoomed.copyInto(result, row * width, sourceIndex, sourceIndex + width)
        }

        return result
    }

    protected open fun chooseModeOrRandom(first: Int, second: Int, third: Int, fourth: Int): Int {
        if (second == third && third == fourth) {
            return second
        }
        if (first == second && first == third) {
            return first
        }
        if (first == second && first == fourth) {
            return first
        }
        if (first == third && first == fourth) {
            return first
        }
        if (first == second && third != fourth) {
            return first
        }
        if (first == third && second != fourth) {
            return first
        }
        if (first == fourth && second != third) {
            return first
        }
        if (second == first && third != fourth) {
            return second
        }
        if (second == third && first != fourth) {
            return second
        }
        if (second == fourth && first != third) {
            return second
        }
        if (third == first && second != fourth) {
            return third
        }
        if (third == second && first != fourth) {
            return third
        }
        if (third == fourth && first != second) {
            return third
        }
        if (fourth == first && second != third) {
            return third
        }
        if (fourth == second && first != third) {
            return third
        }
        if (fourth == third && first != second) {
            return third
        }
        return chooseRandom(first, second, third, fourth)
    }
}

private class FuzzyZoomLayer(seed: Long, parentLayer: GenLayer) : ZoomLayer(seed, parentLayer) {
    override fun chooseModeOrRandom(first: Int, second: Int, third: Int, fourth: Int): Int =
        chooseRandom(first, second, third, fourth)
}

private class AddIslandLayer(seed: Long, parentLayer: GenLayer) : GenLayer(seed) {
    init {
        parent = parentLayer
    }

    override fun getInts(x: Int, z: Int, width: Int, height: Int): IntArray {
        val parentX = x - 1
        val parentZ = z - 1
        val parentWidth = width + 2
        val parentHeight = height + 2
        val parentValues = parent!!.getInts(parentX, parentZ, parentWidth, parentHeight)
        val result = IntArray(width * height)

        for (row in 0 until height) {
            for (column in 0 until width) {
                val northWest = parentValues[column + row * parentWidth]
                val northEast = parentValues[column + 2 + row * parentWidth]
                val southWest = parentValues[column + (row + 2) * parentWidth]
                val southEast = parentValues[column + 2 + (row + 2) * parentWidth]
                val center = parentValues[column + 1 + (row + 1) * parentWidth]

                initChunkSeed((column + x).toLong(), (row + z).toLong())

                result[column + row * width] =
                    if (center == 0 && (northWest != 0 || northEast != 0 || southWest != 0 || southEast != 0)) {
                        var options = 1
                        var chosen = 1

                        if (northWest != 0 && nextInt(options++) == 0) {
                            chosen = northWest
                        }
                        if (northEast != 0 && nextInt(options++) == 0) {
                            chosen = northEast
                        }
                        if (southWest != 0 && nextInt(options++) == 0) {
                            chosen = southWest
                        }
                        if (southEast != 0 && nextInt(options++) == 0) {
                            chosen = southEast
                        }

                        if (nextInt(3) == 0) {
                            chosen
                        } else if (chosen == Beta119Biomes.ICE_PLAINS) {
                            Beta119Biomes.FROZEN_OCEAN
                        } else {
                            0
                        }
                    } else if (center > 0 && (northWest == 0 || northEast == 0 || southWest == 0 || southEast == 0)) {
                        if (nextInt(5) == 0) {
                            if (center == Beta119Biomes.ICE_PLAINS) Beta119Biomes.FROZEN_OCEAN else 0
                        } else {
                            center
                        }
                    } else {
                        center
                    }
            }
        }

        return result
    }
}

private class AddSnowLayer(seed: Long, parentLayer: GenLayer) : GenLayer(seed) {
    init {
        parent = parentLayer
    }

    override fun getInts(x: Int, z: Int, width: Int, height: Int): IntArray {
        val parentX = x - 1
        val parentZ = z - 1
        val parentWidth = width + 2
        val parentHeight = height + 2
        val parentValues = parent!!.getInts(parentX, parentZ, parentWidth, parentHeight)
        val result = IntArray(width * height)

        for (row in 0 until height) {
            for (column in 0 until width) {
                val center = parentValues[column + 1 + (row + 1) * parentWidth]
                initChunkSeed((column + x).toLong(), (row + z).toLong())

                result[column + row * width] =
                    if (center == 0) {
                        0
                    } else if (nextInt(5) == 0) {
                        Beta119Biomes.ICE_PLAINS
                    } else {
                        1
                    }
            }
        }

        return result
    }
}

private class AddMushroomIslandLayer(seed: Long, parentLayer: GenLayer) : GenLayer(seed) {
    init {
        parent = parentLayer
    }

    override fun getInts(x: Int, z: Int, width: Int, height: Int): IntArray {
        val parentX = x - 1
        val parentZ = z - 1
        val parentWidth = width + 2
        val parentHeight = height + 2
        val parentValues = parent!!.getInts(parentX, parentZ, parentWidth, parentHeight)
        val result = IntArray(width * height)

        for (row in 0 until height) {
            for (column in 0 until width) {
                val northWest = parentValues[column + row * parentWidth]
                val northEast = parentValues[column + 2 + row * parentWidth]
                val southWest = parentValues[column + (row + 2) * parentWidth]
                val southEast = parentValues[column + 2 + (row + 2) * parentWidth]
                val center = parentValues[column + 1 + (row + 1) * parentWidth]

                initChunkSeed((column + x).toLong(), (row + z).toLong())

                result[column + row * width] =
                    if (
                        center == 0 &&
                        northWest == 0 &&
                        northEast == 0 &&
                        southWest == 0 &&
                        southEast == 0 &&
                        nextInt(100) == 0
                    ) {
                        Beta119Biomes.MUSHROOM_ISLAND
                    } else {
                        center
                    }
            }
        }

        return result
    }
}

private class RiverInitLayer(seed: Long, parentLayer: GenLayer) : GenLayer(seed) {
    init {
        parent = parentLayer
    }

    override fun getInts(x: Int, z: Int, width: Int, height: Int): IntArray {
        val parentValues = parent!!.getInts(x, z, width, height)
        val result = IntArray(width * height)

        for (row in 0 until height) {
            for (column in 0 until width) {
                initChunkSeed((column + x).toLong(), (row + z).toLong())
                val index = column + row * width
                result[index] = if (parentValues[index] > 0) nextInt(2) + 2 else 0
            }
        }

        return result
    }
}

private class RiverLayer(seed: Long, parentLayer: GenLayer) : GenLayer(seed) {
    init {
        parent = parentLayer
    }

    override fun getInts(x: Int, z: Int, width: Int, height: Int): IntArray {
        val parentX = x - 1
        val parentZ = z - 1
        val parentWidth = width + 2
        val parentHeight = height + 2
        val parentValues = parent!!.getInts(parentX, parentZ, parentWidth, parentHeight)
        val result = IntArray(width * height)

        for (row in 0 until height) {
            for (column in 0 until width) {
                val west = parentValues[column + (row + 1) * parentWidth]
                val east = parentValues[column + 2 + (row + 1) * parentWidth]
                val north = parentValues[column + 1 + row * parentWidth]
                val south = parentValues[column + 1 + (row + 2) * parentWidth]
                val center = parentValues[column + 1 + (row + 1) * parentWidth]

                result[column + row * width] =
                    if (center != 0 && west != 0 && east != 0 && north != 0 && south != 0) {
                        if (center == west && center == east && center == north && center == south) {
                            -1
                        } else {
                            Beta119Biomes.RIVER
                        }
                    } else {
                        Beta119Biomes.RIVER
                    }
            }
        }

        return result
    }
}

private class SmoothLayer(seed: Long, parentLayer: GenLayer) : GenLayer(seed) {
    init {
        parent = parentLayer
    }

    override fun getInts(x: Int, z: Int, width: Int, height: Int): IntArray {
        val parentX = x - 1
        val parentZ = z - 1
        val parentWidth = width + 2
        val parentHeight = height + 2
        val parentValues = parent!!.getInts(parentX, parentZ, parentWidth, parentHeight)
        val result = IntArray(width * height)

        for (row in 0 until height) {
            for (column in 0 until width) {
                val west = parentValues[column + (row + 1) * parentWidth]
                val east = parentValues[column + 2 + (row + 1) * parentWidth]
                val north = parentValues[column + 1 + row * parentWidth]
                val south = parentValues[column + 1 + (row + 2) * parentWidth]
                var center = parentValues[column + 1 + (row + 1) * parentWidth]

                if (west == east && north == south) {
                    initChunkSeed((column + x).toLong(), (row + z).toLong())
                    center = if (nextInt(2) == 0) west else north
                } else {
                    if (west == east) {
                        center = west
                    }
                    if (north == south) {
                        center = north
                    }
                }

                result[column + row * width] = center
            }
        }

        return result
    }
}

private class BiomeLayer(seed: Long, parentLayer: GenLayer) : GenLayer(seed) {
    private val allowedBiomes = intArrayOf(
        Beta119Biomes.DESERT,
        Beta119Biomes.FOREST,
        Beta119Biomes.EXTREME_HILLS,
        Beta119Biomes.SWAMPLAND,
        Beta119Biomes.PLAINS,
        Beta119Biomes.TAIGA,
    )

    init {
        parent = parentLayer
    }

    override fun getInts(x: Int, z: Int, width: Int, height: Int): IntArray {
        val parentValues = parent!!.getInts(x, z, width, height)
        val result = IntArray(width * height)

        for (row in 0 until height) {
            for (column in 0 until width) {
                initChunkSeed((column + x).toLong(), (row + z).toLong())
                val index = column + row * width
                val value = parentValues[index]

                result[index] =
                    when {
                        value == 0 -> 0
                        value == Beta119Biomes.MUSHROOM_ISLAND -> value
                        value == 1 -> allowedBiomes[nextInt(allowedBiomes.size)]
                        else -> Beta119Biomes.ICE_PLAINS
                    }
            }
        }

        return result
    }
}

private class MushroomShoreLayer(seed: Long, parentLayer: GenLayer) : GenLayer(seed) {
    init {
        parent = parentLayer
    }

    override fun getInts(x: Int, z: Int, width: Int, height: Int): IntArray {
        val parentValues = parent!!.getInts(x - 1, z - 1, width + 2, height + 2)
        val parentWidth = width + 2
        val result = IntArray(width * height)

        for (row in 0 until height) {
            for (column in 0 until width) {
                initChunkSeed((column + x).toLong(), (row + z).toLong())
                val center = parentValues[column + 1 + (row + 1) * parentWidth]

                result[column + row * width] =
                    if (center == Beta119Biomes.MUSHROOM_ISLAND) {
                        val north = parentValues[column + 1 + row * parentWidth]
                        val east = parentValues[column + 2 + (row + 1) * parentWidth]
                        val west = parentValues[column + (row + 1) * parentWidth]
                        val south = parentValues[column + 1 + (row + 2) * parentWidth]

                        if (
                            north == Beta119Biomes.OCEAN ||
                            east == Beta119Biomes.OCEAN ||
                            west == Beta119Biomes.OCEAN ||
                            south == Beta119Biomes.OCEAN
                        ) {
                            Beta119Biomes.MUSHROOM_ISLAND_SHORE
                        } else {
                            center
                        }
                    } else {
                        center
                    }
            }
        }

        return result
    }
}

private class RiverMixLayer(seed: Long, private val biomeLayer: GenLayer, private val riverLayer: GenLayer) : GenLayer(seed) {
    override fun initWorldGenSeed(seed: Long) {
        biomeLayer.initWorldGenSeed(seed)
        riverLayer.initWorldGenSeed(seed)
        super.initWorldGenSeed(seed)
    }

    override fun getInts(x: Int, z: Int, width: Int, height: Int): IntArray {
        val biomes = biomeLayer.getInts(x, z, width, height)
        val rivers = riverLayer.getInts(x, z, width, height)
        val result = IntArray(width * height)

        for (index in result.indices) {
            val biome = biomes[index]
            val river = rivers[index]

            result[index] =
                if (biome == Beta119Biomes.OCEAN) {
                    biome
                } else if (river >= 0) {
                    when (biome) {
                        Beta119Biomes.ICE_PLAINS -> Beta119Biomes.FROZEN_RIVER
                        Beta119Biomes.MUSHROOM_ISLAND, Beta119Biomes.MUSHROOM_ISLAND_SHORE ->
                            Beta119Biomes.MUSHROOM_ISLAND_SHORE
                        else -> river
                    }
                } else {
                    biome
                }
        }

        return result
    }
}
