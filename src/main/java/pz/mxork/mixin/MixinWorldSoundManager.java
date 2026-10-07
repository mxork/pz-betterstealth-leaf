package pz.mxork.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import zombie.characters.IsoZombie;

@Mixin(targets = "zombie.WorldSoundManager", remap = false)
public abstract class MixinWorldSoundManager {
    // @Inject(
    //     method = "getHearingMultiplier(Lzombie/characters/IsoZombie;)F",
    //     at = @At(
    //         value = "RETURN"
    //     ),
    //     cancellable = true,
    //     remap = false
    // )
    // private void modifyReturn(
    //         zombie.characters.IsoZombie zombie,
    //         CallbackInfoReturnable<Float> cir
    //         ) {
    //     float original = cir.getReturnValue();
    //     cir.setReturnValue(pz.mxork.Patch.modify_zombie_hearing(zombie, original));
    // }

    // @ModifyVariable(
    //     method = "getBiggestSoundZomb",
    //     index = 13,
    //     at = @At(
    //         value = "STORE",
    //         ordinal = 0
    //     ),
    //     remap = false
    // )
    // private float inspectLoop(float delta) {
    //     pz.mxork.Patch.inspect_biggest_sound_loop(delta);
    //     return delta;
    //     // zombie.WorldSoundManager.ResultBiggestSound original = cir.getReturnValue();
    //     // pz.mxork.Patch.zombie_biggest_sound(zombie, original);
    //     // cir.setReturnValue(pz.mxork.Patch.modify_zombie_hearing(zombie, original));
    // }

    // @Inject(
    //     method = "getBiggestSoundZomb",
    //     at = @At(
    //         value = "RETURN"
    //     ),
    //     cancellable = true,
    //     remap = false
    // )
    // private void inspectReturn(
    //         int x,
    //         int y,
    //         int z,
    //         boolean b,
    //         zombie.characters.IsoZombie zombie,
    //         CallbackInfoReturnable<zombie.WorldSoundManager.ResultBiggestSound> cir
    //         ) {
    //     zombie.WorldSoundManager.ResultBiggestSound original = cir.getReturnValue();
    //     pz.mxork.Patch.zombie_biggest_sound(zombie, original);
    //     // cir.setReturnValue(pz.mxork.Patch.modify_zombie_hearing(zombie, original));
    // }

    @Inject(
        method = "getBiggestSoundZomb",
        at = @At(
            value = "HEAD"
        ),
        cancellable = true,
        remap = false
    )
    private void reimplementBiggestSound(
            final int x, final int y, final int z, final boolean ignoreBySameType, final IsoZombie zombie,
            CallbackInfoReturnable<zombie.WorldSoundManager.ResultBiggestSound> cir
            ) {
        // implicit cancel
        cir.setReturnValue(
            pz.mxork.Patch.getBiggestSoundZomb(zombie, x, y, z)
        );
    }

    @Inject(
        method = "getSoundAttract",
        at = @At(
            value = "HEAD"
        ),
        cancellable = true,
        remap = false
    )
    private void replaceSoundAttract(
            zombie.WorldSoundManager.WorldSound sound, IsoZombie zombie,
            CallbackInfoReturnable<Float> cir
            ) {
        // implicit cancel
        cir.setReturnValue(
            pz.mxork.Patch.getSoundAttract(sound, zombie)
        );
    }
}
