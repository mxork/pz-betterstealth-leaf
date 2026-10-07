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

import zombie.debug.*;
import zombie.characters.IsoZombie;
import zombie.characters.IsoPlayer;
import zombie.characters.IsoGameCharacter;
import zombie.CombatManager;
import zombie.scripting.objects.ItemTag;
import zombie.inventory.types.*;

import pz.mxork.Patch;

@Mixin(CombatManager.class)
public class MixinCombatManager {

  // this is the block we're targeting
  // if (!GameClient.client || isoPlayer.isLocalPlayer()) {
  //     isoPlayer.setCriticalHit(Rand.Next(100) < criticalHitChance);
  //     // here
  //     if (isoPlayer.isAttackFromBehind() && isoPlayer.getAttackVars().closeKill && closestHitTarget instanceof IsoZombie) {
  //         final IsoZombie isoZombie = (IsoZombie)closestHitTarget;
  //         if (isoZombie.target == null && isoPlayer.getPrimaryHandItem() != null && !isoPlayer.getPrimaryHandItem().hasTag(ItemTag.FAKE_SPEAR)) {
  //             isoPlayer.setCriticalHit(true);
  //         }
  //     }
  //     if (isoPlayer.isCriticalHit() && !isoPlayer.getAttackVars().closeKill && !isoPlayer.isDoShove() && weaponType == WeaponType.KNIFE) {
  //         isoPlayer.setCriticalHit(false);
  //     }
  //     if (isoPlayer.getStats().numChasingZombies > 1 && isoPlayer.getAttackVars().closeKill && !isoPlayer.isDoShove() && weaponType == WeaponType.KNIFE) {
  //         isoPlayer.setCriticalHit(false);
  //     }
  // }


  // already checks if target is null, which I think is enough
  @Redirect(
      method = "pressedAttack",
      expect = 1,
      at = @At(
        value = "INVOKE",
        target = "Lzombie/characters/IsoPlayer;isAttackFromBehind()Z",
        ordinal = 0
        ),
      remap = false
  )
  private boolean ignoreBehindCheckSneakAttack(IsoPlayer player) {
      return true;
  }

  // Z, Z, Lzombie/inventory/types/WeaponType;, Ljava/lang/String;, Lzombie/inventory/types/HandWeapon;, I, Lzombie/characters/IsoGameCharacter;, F, F, Lzombie/characters/IsoGameCharacter;, Lzombie/characters/IsoZombie;
  @Inject(
      method = "pressedAttack",
      expect = 1,
      at = @At(
        value = "FIELD",
        target = "Lzombie/characters/IsoZombie;target",
        ordinal = 1 // the second one in pressedAttack
        ),
      locals = LocalCapture.CAPTURE_FAILHARD,
      remap = false
  )
  private void rewriteSneakAttackCheck(IsoPlayer player, CallbackInfo ci, boolean isRemotePlayer, boolean bWasSprinting, WeaponType weaponType, String animVariableWeapon, HandWeapon handWeapon, int variation, IsoGameCharacter closestHitTarget, float closestDist, float criticalHitChance, IsoGameCharacter closestToTarget, IsoZombie zombie) {
      if ((zombie.target != player || !zombie.isTargetLocationKnown() || zombie.getTargetSeenTime() < .5f)
          && player.getPrimaryHandItem() != null
          && !player.getPrimaryHandItem().hasTag(ItemTag.FAKE_SPEAR)) {
          player.setCriticalHit(true);
      }
  }

  @Redirect(
      method = "calculateAttackVars(Lzombie/characters/IsoLivingCharacter;Lzombie/network/fields/hit/AttackVars;)V",
      at = @At(
        value = "INVOKE",
        target = "Lzombie/inventory/types/HandWeapon;getMinRange()F"
        ),
      remap = false
  )
  private float rewriteWeaponMinForCloseKill(zombie.inventory.types.HandWeapon weapon) {
    return 1.2f * weapon.getMinRange();
  }

  // @ModifyConstant(
  //     method = "spottedNew(Lzombie/iso/IsoMovingObject;Z)V",
  //     constant = @Constant(floatValue = 720.f)
  // )
  // private float decreaseBonusSpotTime(float original) {
  //   return 60f;
  // }

}
