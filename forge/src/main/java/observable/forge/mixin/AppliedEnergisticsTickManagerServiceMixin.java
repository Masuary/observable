package observable.forge.mixin;

import observable.forge.AppliedEnergisticsProfilingHooks;
import observable.server.Profiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "appeng.me.service.TickManagerService", remap = false)
public abstract class AppliedEnergisticsTickManagerServiceMixin {
    @Unique private long observable$startNanos;
    @Unique private Profiler.TimingData observable$timingData;

    @Inject(method = "unsafeTickingRequest", at = @At("HEAD"), require = 0)
    private void observable$startAe2TickingRequest(@Coerce Object tracker, int ticksSinceLastCall, CallbackInfoReturnable<Object> cir) {
        observable$timingData = AppliedEnergisticsProfilingHooks.startTickingRequest(tracker);
        if (observable$timingData != null) {
            observable$startNanos = System.nanoTime();
        }
    }

    @Inject(method = "unsafeTickingRequest", at = @At("RETURN"), require = 0)
    private void observable$finishAe2TickingRequest(@Coerce Object tracker, int ticksSinceLastCall, CallbackInfoReturnable<Object> cir) {
        AppliedEnergisticsProfilingHooks.finishTickingRequest(observable$timingData, observable$startNanos);
        observable$timingData = null;
    }
}
