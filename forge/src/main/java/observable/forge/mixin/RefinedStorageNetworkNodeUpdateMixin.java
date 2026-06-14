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
@Mixin(
        targets = {
                "com.refinedmods.refinedstorage.apiimpl.network.node.ConstructorNetworkNode",
                "com.refinedmods.refinedstorage.apiimpl.network.node.CrafterNetworkNode",
                "com.refinedmods.refinedstorage.apiimpl.network.node.DestructorNetworkNode",
                "com.refinedmods.refinedstorage.apiimpl.network.node.DetectorNetworkNode",
                "com.refinedmods.refinedstorage.apiimpl.network.node.ExporterNetworkNode",
                "com.refinedmods.refinedstorage.apiimpl.network.node.ExternalStorageNetworkNode",
                "com.refinedmods.refinedstorage.apiimpl.network.node.FluidInterfaceNetworkNode",
                "com.refinedmods.refinedstorage.apiimpl.network.node.ImporterNetworkNode",
                "com.refinedmods.refinedstorage.apiimpl.network.node.InterfaceNetworkNode",
                "com.refinedmods.refinedstorage.apiimpl.network.node.RootNetworkNode",
                "com.refinedmods.refinedstorage.apiimpl.network.node.SecurityManagerNetworkNode",
                "com.refinedmods.refinedstorage.apiimpl.network.node.StorageMonitorNetworkNode",
                "com.refinedmods.refinedstorage.apiimpl.network.node.diskdrive.DiskDriveNetworkNode",
                "com.refinedmods.refinedstorage.apiimpl.network.node.diskmanipulator.DiskManipulatorNetworkNode",
                "edivad.extrastorage.nodes.AdvancedCrafterNetworkNode",
                "edivad.extrastorage.nodes.AdvancedExporterNetworkNode",
                "edivad.extrastorage.nodes.AdvancedImporterNetworkNode"
        },
        remap = false
)
public abstract class RefinedStorageNetworkNodeUpdateMixin {
    @Unique private long observable$startNanos;
    @Unique private Profiler.TimingData observable$timingData;

    @Inject(method = "update", at = @At("HEAD"), require = 0)
    private void observable$startRsNodeUpdate(CallbackInfo ci) {
        observable$timingData = RefinedStorageProfilingHooks.startNodeUpdate((Object)this);
        if (observable$timingData != null) {
            observable$startNanos = System.nanoTime();
        }
    }

    @Inject(method = "update", at = @At("RETURN"), require = 0)
    private void observable$finishRsNodeUpdate(CallbackInfo ci) {
        RefinedStorageProfilingHooks.finishNodeUpdate(observable$timingData, observable$startNanos);
        observable$timingData = null;
    }
}
