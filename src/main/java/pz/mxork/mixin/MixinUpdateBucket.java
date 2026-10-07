package pz.mxork.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.List;

@Mixin(targets = "zombie.MovingObjectUpdateSchedulerUpdateBucket", remap = false)
public class MixinUpdateBucket {
    @Inject(
        method = "update(I)V", // Targets public void update(final int frameCounter)
        at = @At(
            value = "INVOKE",
            target = "Lzombie/iso/IsoMovingObject;update()V",
            shift = At.Shift.AFTER
        ),
        locals = LocalCapture.CAPTURE_FAILHARD,
        remap = false
    )
    private void injectPostUpdate(
            int frameCounter,
            CallbackInfo ci,
            List<?> fullSimulation,
            int i,
            zombie.iso.IsoMovingObject isoMovingObject,
            zombie.characters.IsoZombie zombie
            ) {
        /*
         * Note: The arguments appended after CallbackInfo ('int i', 'Object isoMovingObject')
         * MUST precisely match the ordering of local variables active at that bytecode offset.
         * Mixin will extract 'isoMovingObject' dynamically for us.
         */

        // Pass the captured object safely over to our isolated core code
        pz.mxork.Patch.update_movingobject(isoMovingObject);
    }
}
