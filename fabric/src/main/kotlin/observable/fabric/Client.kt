package observable.fabric

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents
import observable.client.ObservableClient
import observable.client.Overlay

class Client : ClientModInitializer {
    override fun onInitializeClient() {
        ObservableClient.init()

        WorldRenderEvents.END.register {
            Overlay.render(it.matrixStack(), it.tickDelta(), it.projectionMatrix())
        }
    }
}
