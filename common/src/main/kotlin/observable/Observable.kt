package observable

import observable.server.ProfilingData
import dev.architectury.event.events.common.CommandRegistrationEvent
import dev.architectury.event.events.common.LifecycleEvent
import dev.architectury.utils.GameInstance
import net.minecraft.commands.Commands.argument
import net.minecraft.commands.Commands.literal
import net.minecraft.network.chat.TextComponent
import net.minecraft.network.chat.TranslatableComponent
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import observable.net.BetterChannel
import observable.net.C2SPacket
import observable.net.ClientPacketDispatcher
import observable.net.S2CPacket
import observable.server.LuckPermsPermissions
import observable.server.Profiler
import observable.server.ServerSettings
import observable.server.TypeMap
import org.apache.logging.log4j.LogManager

object Observable {
    const val MOD_ID = "observable"
    const val PROFILE_PERMISSION = "observable.profile"
    const val TELEPORT_PERMISSION = "observable.teleport"

    val CHANNEL = BetterChannel(ResourceLocation("channel/observable"))
    val LOGGER = LogManager.getLogger("Observable")
    val PROFILER: Profiler by lazy { Profiler() }
    var RESULTS: ProfilingData? = null

    fun hasPermission(player: Player) =
        (GameInstance.getServer()?.playerList?.isOp(player.gameProfile) ?: true)
            || (GameInstance.getServer()?.isSingleplayer ?: false)

    fun canRunProfiler(player: Player) =
        hasPermission(player) || LuckPermsPermissions.hasPermission(player, PROFILE_PERMISSION)

    fun canTeleport(player: Player) =
        hasPermission(player) || LuckPermsPermissions.hasPermission(player, TELEPORT_PERMISSION)

    @JvmStatic
    fun init() {
        CHANNEL.register { t: C2SPacket.InitTPSProfile, supplier ->
            val player = supplier.get().player
            if (!canRunProfiler(player)) {
                LOGGER.info("${player.name.contents} lacks permissions to start profiling")
                return@register
            }
            if (PROFILER.notProcessing) PROFILER.startRunning(t.duration, t.sample, supplier.get())
        }

        CHANNEL.register { t: C2SPacket.RequestTeleport, supplier ->
            val player = supplier.get().player
            if (!canTeleport(player)) {
                LOGGER.info("${player.name.contents} lacks permissions to teleport")
                return@register
            }
            GameInstance.getServer()?.allLevels?.filter {
                it.dimension().location().equals(t.level)
            }?.get(0)?.let { level ->
                LOGGER.info("Receive request from ${(player.name as TextComponent).text} in " +
                        "${player.level.dimension().location()} to go to ${level.dimension().location()}")
                Scheduler.SERVER.enqueue {
                    if (player.level != level) with(player.position()) {
                        (player as ServerPlayer).teleportTo(
                            level, x, y, z,
                            player.rotationVector.x, player.rotationVector.y
                        )
                    }
                    t.pos?.apply {
                        LOGGER.info("Moving to ($x, $y, $z) in ${t.level}")
                        player.moveTo(x.toDouble(), y.toDouble(), z.toDouble())
                    }
                    t.entityId?.let {
                        (level as Level).getEntity(it)?.position()?.apply {
                            LOGGER.info("Moving to ($x, $y, $z) in ${t.level}")
                            player.moveTo(this)
                        } ?: player.displayClientMessage(
                            TranslatableComponent("text.observable.entity_not_found", t.level.toString()), true)
                    }
                }
            }
        }

        CHANNEL.register { t: C2SPacket.RequestAvailability, supplier ->
            (supplier.get().player as? ServerPlayer)?.let {
                CHANNEL.sendToPlayer(
                    it,
                    if (canRunProfiler(it)) S2CPacket.Availability.Available
                    else S2CPacket.Availability.NoPermissions
                )
            }
        }

        CHANNEL.register { t: S2CPacket.ProfilingStarted, supplier ->
            ClientPacketDispatcher.handle(t, supplier)
        }

        CHANNEL.register { t: S2CPacket.ProfilingCompleted, supplier ->
            ClientPacketDispatcher.handle(t, supplier)
        }

        CHANNEL.register { t: S2CPacket.ProfilerInactive, supplier ->
            ClientPacketDispatcher.handle(t, supplier)
        }

        CHANNEL.register { t: S2CPacket.ProfilingResult, supplier ->
            ClientPacketDispatcher.handle(t, supplier)
        }
        
        CHANNEL.register { t: S2CPacket.Availability, supplier ->
            ClientPacketDispatcher.handle(t, supplier)
        }

        CHANNEL.register { t: S2CPacket.ConsiderProfiling, supplier ->
            ClientPacketDispatcher.handle(t, supplier)
        }

        LifecycleEvent.SERVER_STARTED.register {
            val thread = Thread.currentThread()
            PROFILER.serverThread = thread
//            ContinuousPerfEval.start()
            LOGGER.info("Registered thread ${thread.name}")
        }

        CommandRegistrationEvent.EVENT.register { dispatcher, dedicated ->
            val cmd = literal("observable")
                .requires { it.hasPermission(4) }
                    .executes {
                        it.source.sendSuccess(TextComponent(ServerSettings.toString()), false)
                        1
                    }
                .then(literal("set").let {
                    ServerSettings::class.java.declaredFields.fold(it) { setCmd, field ->
                        val argType = TypeMap[field.type] ?: return@fold setCmd
                        setCmd.then(literal(field.name).then(argument("newVal", argType())
                            .executes { ctx ->
                                try {
                                    field.isAccessible = true
                                    field.set(ServerSettings, ctx.getArgument("newVal", field.type))
                                    1
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    ctx.source.sendFailure(TextComponent("Error setting value\n${e.toString()}"))
                                    0
                                }
                            }))
                    }
                })

            dispatcher.register(cmd)

        }
    }
}
