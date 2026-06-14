package observable.forge.mixin;

import observable.forge.AppliedEnergisticsProfilingHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "appeng.me.Grid", remap = false)
public abstract class AppliedEnergisticsGridServiceMixin {
    @Redirect(
            method = "onServerEndTick",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/api/networking/IGridServiceProvider;onServerEndTick()V"
            ),
            require = 0
    )
    private void observable$profileAe2GridServiceEndTick(@Coerce Object service) {
        AppliedEnergisticsProfilingHooks.runGridServiceEndTick((Object)this, service);
    }
}
