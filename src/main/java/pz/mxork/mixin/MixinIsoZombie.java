package pz.mxork.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

// import com.llamalad7.mixinextras.sugar.Local;
// import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
// import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import zombie.debug.*;
import zombie.characters.IsoZombie;

import java.nio.ByteBuffer;

import pz.mxork.Patch;

@Mixin(IsoZombie.class)
public class MixinIsoZombie {
    // @Overwrite(remap = false)
    // public void spotted(zombie.iso.IsoMovingObject other, boolean bForced) {
    //     Patch.spotted((zombie.characters.IsoPlayer) other, (zombie.characters.IsoZombie) (Object) this);

    //     return;
    // }
    //
    // Unique transient field to safely cache 'other' per zombie instance
    @Unique
    private transient zombie.iso.IsoMovingObject other;

    // // --- 1. CAPTURE ARGUMENTS AT HEAD ---
    @Inject(
        method = "spottedNew(Lzombie/iso/IsoMovingObject;Z)V",
        at = @At("HEAD"),
        remap = false
    )
    private void captureSpottedArgs(zombie.iso.IsoMovingObject other, boolean bForced, CallbackInfo ci) {
        this.other = other;
    }

   // --- BYPASS VIEW DISTANCE CHECK ---
    @Redirect(
        method = "spottedNew(Lzombie/iso/IsoMovingObject;Z)V",
        at = @At(value = "INVOKE", target = "Lzombie/iso/Vector2;getLength()F", ordinal = 0),
        remap = false
    )
    private float bypassViewDistCheck(zombie.iso.Vector2 instance) {
        return 0.0f; // 0.0f is never > viewDist; the early return block is bypassed
    }

    @ModifyConstant(
        method = "spottedNew(Lzombie/iso/IsoMovingObject;Z)V",
        constant = @Constant(floatValue = 720.f)
    )
    private float decreaseBonusSpotTime(float original) {
      return 60f;
    }

    // add back in some of the hunting time
    @Inject(
        method = "getSandboxMemoryDuration",
        at = @At(value = "RETURN"),
        cancellable = true
    )
    private void increaseMemory(CallbackInfoReturnable<Integer> cir) {
      int original = cir.getReturnValue();
      cir.setReturnValue(original + (720-60));
    }

    // --- MAIN INJECTION POINT ---
    @ModifyVariable(
        method = "spottedNew",
        index = 20, // success
        at = @At(
            // at loading game client
            value = "FIELD",
            opcode = 178,
            ordinal = 18
        ),
        remap = false
    )
    private boolean injectSpotted(boolean originalValue) {
        return pz.mxork.Patch.spotted((zombie.characters.IsoPlayer) this.other, (IsoZombie) (Object) this);
    }

    @ModifyVariable(
        method = "spottedNew",
        index = 22, // canAddXPForPerk
        at = @At(
          value = "STORE",
          ordinal = 0
        ),
        remap = false
    )
    private boolean disableVanillaXP(boolean originalValue) {
        return false;
    }

    @Inject(
        method = "renderlast()V",
        at = @At(
            value = "RETURN"
            // target = "Lzombie/characters/IsoGameCharacter;render(FFFLzombie/core/textures/ColorInfo;ZZLzombie/core/opengl/Shader;)V"
        )
        // remap = false
    )
    private void injectDebugDraw(CallbackInfo ci) {
        pz.mxork.Patch.render_zombie_debug((IsoZombie) (Object) this);
    }

    @Redirect(
        method = "RespondToSound",
        expect = 1,
        at = @At(
            value = "INVOKE",
            target = "Lzombie/core/random/Rand;Next(II)I",
            ordinal = 1
        ),
        remap = false
    )
    private int modifySoundX(int lo, int hi) {
        IsoZombie zombie = (IsoZombie) (Object) this;
        return pz.mxork.Patch.modify_zombie_sound_random_offset(zombie, hi);
    }

    @Redirect(
        method = "RespondToSound",
        expect = 1,
        at = @At(
            value = "INVOKE",
            target = "Lzombie/core/random/Rand;Next(II)I",
            ordinal = 2
        ),
        remap = false
    )
    private int modifySoundY(int lo, int hi) {
        IsoZombie zombie = (IsoZombie) (Object) this;
        return pz.mxork.Patch.modify_zombie_sound_random_offset(zombie, hi);
    }

    @ModifyConstant(
        method = "shouldStopThumpingToRespondToSound",
        constant = @Constant(
          floatValue = 0f,
          ordinal = 2 // the last one, with Dot
        )
    )
    private float modifyDotThreshold(float original) {
        return 0.4f;
    }

    @Inject(
        method = "resetForReuse",
        at = @At(value = "RETURN")
    )
    private void injectReset(CallbackInfo ci) {
        pz.mxork.Patch.reset_zombie((IsoZombie) (Object) this);
    }

    @Inject(
      method = "save",
      at = @At(value = "TAIL")
    )
    private void injectSave(final ByteBuffer buffer, final boolean b, CallbackInfo ci) {
        pz.mxork.Patch.save_zombie((IsoZombie) (Object) this, buffer);
    }

    @Inject(
      method = "load",
      at = @At(value = "TAIL")
    )
    private void injectLoad(final ByteBuffer buffer, final int n, final boolean b, CallbackInfo ci) {
        pz.mxork.Patch.load_zombie((IsoZombie) (Object) this, buffer);
    }
}
