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

import pz.mxork.Patch;

@Mixin(IsoPlayer.class)
public class MixinIsoPlayer {
    @Inject(
        method = "renderlast",
        at = @At( value = "TAIL")
    )
    private void injectDebugDraw(CallbackInfo ci) {
        pz.mxork.Patch.render_player_debug((IsoPlayer) (Object) this);
    }

    @Redirect(
        method = "updateAimingStance",
        at = @At(
          value = "INVOKE",
          target = "Lzombie/inventory/types/HandWeapon;getMinRange()F"
          ),
        remap = false
    )
    private float rewriteWeaponMinForCloseKill(zombie.inventory.types.HandWeapon weapon) {
      return 1.2f * weapon.getMinRange();
    }

    // @Inject(
    //     method = "update(V)V",
    //     expect = 1,
    //     at = @At( value = "TAIL")
    // )
    // private void update_player(CallbackInfo ci) {
    //     pz.mxork.Patch.update_player((IsoPlayer) (Object) this);
    // }
}
