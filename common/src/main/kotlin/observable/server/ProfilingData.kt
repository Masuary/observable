@file:UseSerializers(
    EntitySerializer::class, ResourceLocationSerializer::class,
    BlockEntitySerializer::class, BlockPosSerializer::class
)

package observable.server

import dev.architectury.platform.Platform
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import net.minecraft.SystemReport
import net.minecraft.core.BlockPos
import net.minecraft.core.Registry
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import observable.Observable
import observable.net.BlockEntitySerializer
import observable.net.BlockPosSerializer
import observable.net.EntitySerializer
import observable.net.ResourceLocationSerializer
import kotlin.math.roundToInt

@Serializable
data class ProfilingData(
    val entities: Map<ResourceLocation, List<Entry<Int>>>,
    val blocks: Map<ResourceLocation, List<Entry<BlockPos>>>,
    val chunks: ChunkMap,
    val traces: SerializedTraceMap?,
    val ticks: Int,
    val diagnostics: Diagnostics
) {
    companion object {
        fun create(
            entities: Map<Entity, Profiler.TimingData>,
            blocks: Map<ResourceKey<Level>, Map<Profiler.BlockTimingKey, Profiler.TimingData>>,
            ticks: Int,
            traceMap: TraceMap? = null
        ): ProfilingData {
            val chunks = ChunkMapBuilder()

            val entityEntries = entities.map { (entity, data) ->
                chunks.tick(entity, data)
                Entry(entity, Registry.ENTITY_TYPE.getKey(entity.type).toString(), data)
            }.groupBy { it.obj.level.dimension().location() }.mapValues { (_, entry) ->
                entry.map { Entry(it.obj.id, it.type, it.rate, it.ticks, it.traces) }
            }

            val blockEntries = blocks.map { (level, posMap) ->
                level.location() to posMap.map { (key, data) ->
                    chunks.tick(level, key.pos, data)
                    Entry(key.pos, data.name, data)
                }
            }.toMap()

            val diagnostics = Diagnostics(
                Platform.getMod(Observable.MOD_ID).version,
                SystemReport().toLineSeparatedString().split(System.lineSeparator()),
                Platform.getMods().map { mod -> "${mod.name} ${mod.version}" }
            )

            return ProfilingData(
                entityEntries,
                blockEntries,
                chunks.build(ticks),
                traceMap?.let { SerializedTraceMap.create(it) },
                ticks,
                diagnostics
            )
        }
    }

    @Serializable
    data class SerializedChunkPos(val x: Int, val z: Int) {
        constructor(pos: ChunkPos) : this(pos.x, pos.z)
        constructor(pos: BlockPos) : this(ChunkPos(pos))
        constructor(pos: Vec3) : this(pos.x.roundToInt() / 16, pos.z.roundToInt() / 16)
    }

    @Serializable
    data class SerializedChunkEntry(var time: Long) {
        constructor() : this(0L)
    }

    class ChunkMapBuilder {
        val data = HashMap<ResourceLocation, HashMap<SerializedChunkPos, SerializedChunkEntry>>()

        fun tick(entity: Entity, timings: Profiler.TimingData) {
            val entry = data.getOrPut(entity.level.dimension().location()) { HashMap() }
                .getOrPut(SerializedChunkPos(entity.blockPosition())) { SerializedChunkEntry() }
            entry.time += timings.time
        }

        fun tick(levelKey: ResourceKey<Level>, pos: BlockPos, timings: Profiler.TimingData) {
            val entry = data.getOrPut(levelKey.location()) { HashMap() }
                .getOrPut(SerializedChunkPos(pos)) { SerializedChunkEntry() }
            entry.time += timings.time
        }

        fun build(ticks: Int): ChunkMap = data.toMap().mapValues { (_, value) ->
            value.toList().map { (pos, entry) ->
                Pair(pos, entry.time.toDouble() / ticks)
            }.sortedByDescending { it.second }
        }
    }

    @Serializable
    data class Entry<T>(
        val obj: T,
        val type: String,
        val rate: Double,
        val ticks: Int,
        val traces: SerializedTraceMap
    ) {
        constructor(obj: T, type: String, data: Profiler.TimingData) : this(
            obj,
            type,
            data.time.toDouble() / data.ticks.toDouble(),
            data.ticks,
            SerializedTraceMap.create(data.traces)
        )
    }

    @Serializable
    data class Diagnostics(
        val observableVersion: String,
        val systemReport: List<String>,
        val mods: List<String>
    )

    @Serializable
    data class SerializedStackTrace(
        val classname: String,
        val fileName: String?,
        val lineNumber: Int,
        val methodName: String
    ) {
        constructor(el: StackTraceElement) : this(el.className, el.fileName, el.lineNumber, el.methodName)
    }

    @Serializable
    data class SerializedTraceMap(
        val className: String,
        val methodName: String,
        val children: List<SerializedTraceMap>,
        val count: Int
    ) {
        companion object {
            fun create(traceMap: TraceMap): SerializedTraceMap {
                Remapper.transform(traceMap)

                return SerializedTraceMap(
                    traceMap.className,
                    traceMap.methodName,
                    traceMap.children.map { (_, map) ->
                        Remapper.transform(map)
                        SerializedTraceMap.create(map)
                    }.sortedByDescending { it.count },
                    traceMap.count
                )
            }
        }

        val classMethod get() = "$className.$methodName"
    }
}

typealias ChunkMap = Map<ResourceLocation, List<Pair<ProfilingData.SerializedChunkPos, Double>>>
