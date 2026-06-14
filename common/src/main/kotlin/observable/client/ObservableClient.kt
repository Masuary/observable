package observable.client

import com.mojang.blaze3d.platform.InputConstants
import dev.architectury.event.events.client.ClientLifecycleEvent
import dev.architectury.event.events.client.ClientPlayerEvent
import dev.architectury.event.events.client.ClientTickEvent
import dev.architectury.networking.NetworkManager
import dev.architectury.registry.client.keymappings.KeyMappingRegistry
import dev.architectury.utils.GameInstance
import net.minecraft.ChatFormatting
import net.minecraft.client.KeyMapping
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.TranslatableComponent
import observable.Observable
import observable.net.S2CPacket
import org.lwjgl.glfw.GLFW
import java.util.function.Supplier

object ObservableClient {
    val PROFILE_KEYBIND by lazy {
        KeyMapping(
            "key.observable.profile",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            "category.observable.keybinds"
        )
    }

    val PROFILE_SCREEN by lazy { ProfileScreen() }

    @JvmStatic
    fun init() {
        KeyMappingRegistry.register(PROFILE_KEYBIND)

        ClientTickEvent.CLIENT_POST.register {
            if (PROFILE_KEYBIND.consumeClick()) {
                it.setScreen(PROFILE_SCREEN)
            }
        }

        ClientLifecycleEvent.CLIENT_LEVEL_LOAD.register {
            Overlay.loadSync(it)
        }

        ClientPlayerEvent.CLIENT_PLAYER_QUIT.register {
            Observable.RESULTS = null
            PROFILE_SCREEN.action = ProfileScreen.Action.UNAVAILABLE
        }
    }

    fun handle(packet: S2CPacket.ProfilingStarted, supplier: Supplier<NetworkManager.PacketContext>) {
        PROFILE_SCREEN.action = ProfileScreen.Action.TPSProfilerRunning(packet.endMillis)
        PROFILE_SCREEN.startBtn?.active = false
    }

    fun handle(packet: S2CPacket.ProfilingCompleted, supplier: Supplier<NetworkManager.PacketContext>) {
        PROFILE_SCREEN.action = ProfileScreen.Action.TPSProfilerCompleted
    }

    fun handle(packet: S2CPacket.ProfilerInactive, supplier: Supplier<NetworkManager.PacketContext>) {
        PROFILE_SCREEN.action = ProfileScreen.Action.DEFAULT
        PROFILE_SCREEN.startBtn?.active = true
    }

    fun handle(packet: S2CPacket.ProfilingResult, supplier: Supplier<NetworkManager.PacketContext>) {
        Observable.RESULTS = packet.data
        PROFILE_SCREEN.apply {
            action = ProfileScreen.Action.DEFAULT
            startBtn?.active = true
            arrayOf(resultsBtn, overlayBtn).forEach { it.active = true }
        }
        val data = packet.data.entities
        Observable.LOGGER.info("Received profiling result with ${data.size} entries")
        Overlay.loadSync()
    }

    fun handle(packet: S2CPacket.Availability, supplier: Supplier<NetworkManager.PacketContext>) {
        when (packet) {
            S2CPacket.Availability.Available -> {
                PROFILE_SCREEN.action = ProfileScreen.Action.DEFAULT
                PROFILE_SCREEN.startBtn?.active = true
            }
            S2CPacket.Availability.NoPermissions -> {
                PROFILE_SCREEN.action = ProfileScreen.Action.NO_PERMISSIONS
                PROFILE_SCREEN.startBtn?.active = false
            }
        }
    }

    fun handle(packet: S2CPacket.ConsiderProfiling, supplier: Supplier<NetworkManager.PacketContext>) {
        if (ProfileScreen.HAS_BEEN_OPENED) return
        Observable.LOGGER.info("Notifying player")
        val tps = "%.2f".format(packet.tps)
        GameInstance.getClient().gui.chat.addMessage(
            TranslatableComponent(
                "text.observable.suggest",
                tps,
                TranslatableComponent("text.observable.suggest_action")
                    .withStyle(ChatFormatting.UNDERLINE)
                    .withStyle {
                        it.withClickEvent(object : ClickEvent(null, "") {
                            override fun getAction(): Action? {
                                GameInstance.getClient().setScreen(PROFILE_SCREEN)
                                return null
                            }
                        })
                    }
            )
        )
    }
}
