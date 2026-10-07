package pz.mxork.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import zombie.debug.*;
import zombie.characters.IsoPlayer;
import zombie.characters.IsoGameCharacter;
import zombie.WorldSoundManager;

import pz.mxork.Patch;

@Mixin(IsoGameCharacter.class)
public abstract class MixinGameCharacter {
    @Unique
    private transient float original_volume;
    @Inject(
        method = "DoFootstepSound(F)V",
        expect = 1,
        at = @At("HEAD"),
        remap = false
    )
    private void capture_original_volume(float original_volume, CallbackInfo ci) {
        this.original_volume = original_volume;
    }

    @Redirect(
        method = "DoFootstepSound(F)V",
        expect = 1,
        at = @At(
          value = "INVOKE",
          target = "Lzombie/WorldSoundManager;addSound",
          // target = "Lzombie/WorldSoundManager;addSound(Ljava/lang/Object;IIIIIZFFZZZ)Lzombie/WorldSoundManager$WorldSound",
          remap = false
          )
    )
    private WorldSoundManager.WorldSound modifyFootstep(final WorldSoundManager instance, final Object source, final int x, final int y, final int z, final int radius, final int volume, final boolean stressHumans, final float zombieIgnoreDist, final float stressMod, final boolean sourceIsZombie, final boolean doSend, final boolean remote
        ) {
      return pz.mxork.Patch.modify_footstep_addsound(this.original_volume, instance, source, x, y, z, radius, volume, stressHumans, zombieIgnoreDist, stressMod, sourceIsZombie, doSend, remote);
      // return instance.addSound(source, x, y, z, radius, volume, stressHumans, zombieIgnoreDist, stressMod, sourceIsZombie, doSend, remote);
    }
}
