package observable.forge.mixin;

import observable.forge.RefinedStorageProfilingHooks;
import observable.server.Profiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.refinedmods.refinedstorage.apiimpl.network.Network", remap = false)
public abstract class RefinedStorageNetworkUpdateMixin {
    @Unique private long observable$startNanos;
    @Unique private Profiler.TimingData observable$timingData;

    @Inject(method = "update", at = @At("HEAD"), require = 0)
    private void observable$startRsNetworkUpdate(CallbackInfo ci) {
        observable$timingData = RefinedStorageProfilingHooks.startNetworkUpdate((Object)this);
        if (observable$timingData != null) {
            observable$startNanos = System.nanoTime();
        }
    }

    @Inject(method = "update", at = @At("RETURN"), require = 0)
    private void observable$finishRsNetworkUpdate(CallbackInfo ci) {
        RefinedStorageProfilingHooks.finishNodeUpdate(observable$timingData, observable$startNanos);
        observable$timingData = null;
    }
}
