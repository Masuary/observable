package observable.net

import dev.architectury.networking.NetworkManager
import dev.architectury.platform.Platform
import dev.architectury.utils.Env
import observable.Observable
import java.util.function.Supplier

object ClientPacketDispatcher {
    private const val CLIENT_HANDLER = "observable.client.ObservableClient"

    fun handle(packet: S2CPacket.ProfilingStarted, supplier: Supplier<NetworkManager.PacketContext>) =
        invoke("handle", S2CPacket.ProfilingStarted::class.java, packet, supplier)

    fun handle(packet: S2CPacket.ProfilingCompleted, supplier: Supplier<NetworkManager.PacketContext>) =
        invoke("handle", S2CPacket.ProfilingCompleted::class.java, packet, supplier)

    fun handle(packet: S2CPacket.ProfilerInactive, supplier: Supplier<NetworkManager.PacketContext>) =
        invoke("handle", S2CPacket.ProfilerInactive::class.java, packet, supplier)

    fun handle(packet: S2CPacket.ProfilingResult, supplier: Supplier<NetworkManager.PacketContext>) =
        invoke("handle", S2CPacket.ProfilingResult::class.java, packet, supplier)

    fun handle(packet: S2CPacket.Availability, supplier: Supplier<NetworkManager.PacketContext>) =
        invoke("handle", S2CPacket.Availability::class.java, packet, supplier)

    fun handle(packet: S2CPacket.ConsiderProfiling, supplier: Supplier<NetworkManager.PacketContext>) =
        invoke("handle", S2CPacket.ConsiderProfiling::class.java, packet, supplier)

    private fun invoke(
        methodName: String,
        packetClass: Class<*>,
        packet: Any,
        supplier: Supplier<NetworkManager.PacketContext>
    ) {
        if (Platform.getEnvironment() != Env.CLIENT) return

        try {
            val handler = Class.forName(CLIENT_HANDLER)
            val instance = handler.getField("INSTANCE").get(null)
            handler.getMethod(methodName, packetClass, Supplier::class.java).invoke(instance, packet, supplier)
        } catch (e: Throwable) {
            Observable.LOGGER.warn("Unable to dispatch Observable client packet ${packetClass.name}: ${e.message}", e)
        }
    }
}
