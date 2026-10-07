package pz.mxork;

import java.util.*;
import java.util.function.*;
import zombie.characters.*;
import zombie.network.*;
import zombie.iso.*;
import zombie.iso.areas.*;
import zombie.vehicles.*;
import zombie.iso.SpriteDetails.IsoFlagType;
import zombie.iso.SpriteDetails.IsoObjectType;
import zombie.GameTime;
import zombie.gameStates.IngameState;
import zombie.core.opengl.RenderSettings;
import zombie.core.textures.*;
import zombie.debug.DebugLog;
import zombie.debug.DebugOptions;
import zombie.debug.DebugType;
import zombie.debug.LineDrawer;
import zombie.core.*;
import zombie.core.random.*;
import zombie.characters.skills.PerkFactory;
import zombie.iso.weather.*;
import zombie.iso.objects.IsoTree;
import zombie.core.skinnedmodel.visual.IHumanVisual;
import zombie.util.*;
import zombie.util.list.*;
import zombie.SandboxOptions;
import zombie.ai.states.*;
import zombie.ai.*;
import zombie.ui.TextManager;
import zombie.scripting.objects.*;
import zombie.WorldSoundManager;
import zombie.scripting.objects.ItemBodyLocation;
import zombie.inventory.types.*;
import zombie.pathfind.*;
import zombie.core.properties.PropertyContainer;
import zombie.core.skinnedmodel.animation.AnimationPlayer;

import org.joml.Vector3f;

public final class Patch
{
  // NOTICE
  private static class Notice {
    public HashMap<IsoPlayer, NoticePlayer> forPlayer;
    public float v2; // irritation
    public float v3; // hear
    public float v4; // sound noise
    public float alertTime;
    public Notice() {
      this.forPlayer = new HashMap();
      this.v2 = 0f;
      this.v3 = 0f;
      this.v4 = 0f;
      this.alertTime = 0f;
    }
  }

  private static class NoticePlayer {
    public float v1; // spot
    public NoticePlayer() {
      this.v1 = 0;
    }
  }

  // STATIC
  // :note v2 should probably be zombie-scoped, not player,zombie
  public static WeakHashMap<IsoZombie, Notice> notice_levels;
  public static final float l1 = .9f;
  public static final float t1 = 0.3f;
  public static final float l2 = .5f;
  public static final float t2 = 10.f;
  public static final float l3 = .5f;
  public static final float t3 = 3.f;
  public static final float l4 = .5f;
  public static final float t4 = 5.f;

  private static Vector2 pzv;
  private static Vector2 pov;
  private static Vector2 zlv;

  private static Vector3 dist1;
  private static Vector3 dist2;

  private static Vector2 zsv2;
  private static Vector3 zsv3;

  private static Vector2 closest1;
  private static Vector2 closest2;
  private static Vector2 projection_target;
  private static Vector2 projection;

  private static ArrayList<BaseVehicle> nearVehicles;
  private static Vector2 intersection;

  static {
    notice_levels= new WeakHashMap();

    pzv = new Vector2();
    pov = new Vector2();
    zlv = new Vector2();

    dist1 = new Vector3();
    dist2 = new Vector3();

    zsv2 = new Vector2();
    zsv3 = new Vector3();

    closest1 = new Vector2();
    closest2 = new Vector2();
    projection_target = new Vector2();
    projection = new Vector2();

    nearVehicles = new ArrayList();
    intersection = new Vector2();
  }

  // MAIN
  public static boolean spotted_report(final IsoPlayer player, final IsoZombie zombie, float shelterMod, float carObstacleMod) {
    if (shelterMod != 1.f) {
      logf("shelter mod", shelterMod);
    }
    if (carObstacleMod != 1.f) {
      logf("car obstacle mod", carObstacleMod);
    }
    return false;
  }

  public static boolean spotted(final IsoPlayer player, final IsoZombie zombie) {
    final Notice notice = getNotice(zombie);
    final NoticePlayer notice_player = getNoticeForPlayer(zombie, player);
    int playerIndex = player.getPlayerIndex();

    // squares
    final IsoGridSquare c = player.getCurrentSquare();
    final IsoGridSquare playerSquare = player.getCurrentSquare();
    final IsoGridSquare zombieSquare = zombie.getCurrentSquare();

    final IsoGridSquare cn = c.getAdjacentSquare(IsoDirections.N);
    final IsoGridSquare cs = c.getAdjacentSquare(IsoDirections.S);
    final IsoGridSquare ce = c.getAdjacentSquare(IsoDirections.E);
    final IsoGridSquare cw = c.getAdjacentSquare(IsoDirections.W);

    final IsoGridSquare cne = c.getAdjacentSquare(IsoDirections.NE);
    final IsoGridSquare cnw = c.getAdjacentSquare(IsoDirections.NW);
    final IsoGridSquare cse = c.getAdjacentSquare(IsoDirections.SE);
    final IsoGridSquare csw = c.getAdjacentSquare(IsoDirections.SW);

    // light

    final ColorInfo zombieSquareLightInfo = zombieSquare.lighting[playerIndex].lightInfo();
    final float zombieSquareLightLevel = (zombieSquareLightInfo.r + zombieSquareLightInfo.g + zombieSquareLightInfo.b) / 3.0f;

    // room
    final IsoRoom playerRoom = playerSquare.getRoom();
    final IsoRoom zombieRoom = zombieSquare.getRoom();

    // z-level
    final int zLevelDifference = Math.abs(playerSquare.getZ() - zombieSquare.getZ());
    final float zLevelDifferenceFactor = 1.0f / (1.0f + zLevelDifference*5*3);

    // player predicates
    final boolean isAiming = player.isAiming();
    final boolean isSneaking = player.isSneaking();
    final boolean isRunning = player.isRunning();
    final boolean isSprinting = player.isSprinting();
    final boolean isAttacking = player.isAttacking();
    final boolean isPerformingAction = player.isPerformingAnAction();
    final boolean isUsingTorch = player.getTorchStrength() > 0.0f;
    final boolean isConspicuous = player.hasTrait(CharacterTrait.CONSPICUOUS);
    final boolean isInconspicuous = player.hasTrait(CharacterTrait.INCONSPICUOUS);
    final boolean isOutside = playerSquare.getRoom() == null;
    final boolean isInVehicle = player.getVehicle() == null;

    // zombie predicates
    final boolean isZombieProne = zombie.isProne();
    final boolean isZombieEatingBody = zombie.eatBodyTarget != null;
    final boolean isZombieInactive = zombie.inactive;
    final boolean isZombieFakeDead = zombie.isFakeDead();
    final boolean isZombieCrawling = zombie.isCrawling();
    final boolean isZombieAttacking = zombie.isAttacking();
    final int zombieSightLevel = zombie.sight; // 1,2,3 is ascending badness
    final float zombieSightLevelMod = (1.5f - .25f*zombieSightLevel); // 1,2,3 is ascending badness
    final boolean isZombieOutside = zombieSquare.getRoom() == null;
    // final float zombieTimeSinceSeenFlesh = zombie.TimeSinceSeenFlesh;
    final float zombieTargetSeenTime = zombie.getTargetSeenTime();
    final boolean isZombieTargetLocationKnown = zombie.isTargetLocationKnown();
    final IsoMovingObject zombieOriginalTarget = zombie.getTarget();
    final IsoMovingObject zombieSpottedLast = zombie.spottedLast;
    final boolean isDifferentPlayerTarget = zombieOriginalTarget != null && player != zombieOriginalTarget;

    // player sneak
    final float playerSneakX = playerSneakLevelFactor(player);

    // view dist
    final float viewDistance = GameTime.getInstance().getViewDist();
    final float viewDistanceMax = GameTime.getInstance().getViewDistMax();

    // distance, player to zombie vector
    // look angle
    final float acuity = zombieAcuityAtPlayerLocation(zombie, player);

    // visibility
    final float visibilityNoCover = playerVisibility(player);

    // noise
    final float weatherNoise = max(weatherNoiseLevel(player), weatherNoiseLevel(zombie));
    final float objectNoise = objectNoiseLevel(player);
    final float noise = weatherNoise + objectNoise;

    // cover
    // I would like to replace the nearwall calculations with an integral along Wu's or Bresenhams,
    // potentially fuzzed around the player location.
    //
    // full fog is 6f
    // object noise is generally 4ish in bushy areas
    // others are 6
    //
    // could fractional power the sum to limit stacking, or take max
    final float nearBase = isSneaking ? 6 : 1;
    final float nearVehicleSneakRaw = nearVehicleFactor(player, zombie); // 0,1
    final float nearVehicleSneak = 8f*nearVehicleSneakRaw;
    final float nearWallSneakRaw = nearWallFactor(player, zombie); // 0,1
    final float nearWallSneak = 8f*nearWallSneakRaw; // pow(nearWallSneakRaw, 0.6f); // pow(nearWallSneakRaw/4.f, 0.4f);
    final float totalCover = nearWallSneak + nearVehicleSneak + noise;
    final float cover =
      pow(3.f/(3.f + totalCover), 1.f+playerSneakX) // the stretch here might be too strong
      ;

    if (false) {
      log("..");
      logf("cover", cover);
      logf("  near vehicle", nearVehicleSneak);
      logf("    raw", nearVehicleSneakRaw);
      logf("  near wall", nearWallSneak);
      logf("    raw", nearWallSneakRaw);
      logf("  noise", noise);
      logf("    weather", weatherNoise);
      logf("    objects", objectNoise);
    }

    // delta
    final float epsilon = acuity * visibilityNoCover * cover; // + ind(!isDifferentPlayerTarget && isZombieTargetLocationKnown); // pretty sure can't do isZombieTargetLocationKnown because it relies on spotted to change values (or breaking LoS entirely)
    final float k1 = kc(l1, t1);
    final float delta = (1.f-k1)*epsilon;

    // apply delta
    notice_player.v1 += delta;
    notice_player.v1 = clamp01(notice_player.v1);

    // if (epsilon >= .6f && playerVisibilityFromMovement(player) > 1f) {
    //   final Vector2 turnv = directionCharacters(zombie, player);
    //   alertLocation.x = player.getX();
    //   alertLocation.y = player.getY();
    //   zombieAlert(zombie, alertLocation);
    // }

    // log
    if (false) {
      log("..");
      logf("epsilon", epsilon);
      logf("v1", notice_player.v1);
      logf("v2", notice.v2);
    }

    return notice_player.v1 >= 1.f;
  }

  // as fun as this is, I don't like how its playing
  static Vector2 alertLocation;
  static {
    alertLocation = new Vector2();
  }
  public static void zombieAlert(IsoZombie zombie, Vector2 location) {
    final Notice notice = getNotice(zombie);
    if (notice.alertTime > 0f) {
      return;
    }

    final Vector2 direction = pzv;
    direction.x = location.x - zombie.getX();
    direction.y = location.y - zombie.getY();
    direction.normalize();
    final float dot = zombie.getForwardDirection().dot(direction);
    if (dot < .3f) {
      direction.rotate((((float)Math.PI)/6f)*Rand.Next(-1f,1f));
      direction.setLength(3f);
      zombie.setTurnAlertedValues((int) (direction.getX()+zombie.getX()), (int) (direction.getY()+zombie.getY()));
      // :note ZombieTurnAlerted invokes path finding on exit, so we need to give it something
      direction.setLength(.2f);
      zombie.pathToLocationF(zombie.getX() + direction.getX(), zombie.getY() + direction.getY(), zombie.getZ());
      zombie.allowRepathDelay = 0.0f;
    } else {
      // turn without animation
      // see IsoGameCharacter.facePosition
      direction.rotate((((float)Math.PI)/9f)*Rand.Next(-1f,1f));
      zombie.setForwardDirection(direction);
      final AnimationPlayer animationPlayer = zombie.getAnimationPlayer();
      if (animationPlayer != null && animationPlayer.isReady()) {
          animationPlayer.updateForwardDirection(zombie);
      }
      // start walking towards, would also need location z
      // zombie.pathToLocationF(location.x, location.y, zombie.getZ());
      // zombie.allowRepathDelay = 0.0f;
    }

    notice.alertTime = 60f;
  }

  // XP
  public static HashMap<IsoPlayer, Float> delayedSneakXP;
  public static HashMap<IsoPlayer, Float> delayedLightfootXP;
  static {
    delayedSneakXP = new HashMap();
    delayedLightfootXP = new HashMap();
  }
  public static void awardSneakXP(IsoPlayer player) {
    float amount = 1f;
    delayedSneakXP.put(player, delayedSneakXP.getOrDefault(player, 0f)+amount);
  }

  public static void awardLightfootXp(IsoPlayer player, WorldSoundManager.WorldSound sound, IsoZombie zombie) {
    float d = distanceCharacters(player, zombie);
    float amount = (10f/(10f+d));
    delayedLightfootXP.put(player, delayedLightfootXP.getOrDefault(player, 0f)+amount);
  }

  // UPDATERS
  public static void update_movingobject(Object isoMovingObject) {
      if (isoMovingObject instanceof IsoPlayer p) {
          update_player(p);
      }
      if (isoMovingObject instanceof IsoZombie z) {
          update_zombie(z);
      }
  }

  // this gets called 3 times per tick???
  // clearly from
  //     GameTime.getInstance().perObjectMultiplier = (float)this.frameMod;
  // is this correct if I'm using getTimeDelta?
  // its gone now. not sture what was happening
  public static HashMap<IsoPlayer, DCover> player_dcover;
  static {
    player_dcover = new HashMap();
  }
  public static DCover directionalCoverFor(IsoPlayer player) {
    return player_dcover.computeIfAbsent(player, p -> new DCover());
  }
  public static void update_player(IsoPlayer player) {
    if (false) {
      log("--- update player");
      log("index %d", player.getPlayerIndex());
      logf("ambient", actualAmbient(player.getPlayerIndex()));
      logf("square", squareLightLevel(gimmeSquare(player), player.getPlayerIndex()));
    }

    updateDirectionalCover(player);
    DCover directionalCover = directionalCoverFor(player);
    if (player.isSneaking()) {
      player.setNearWallCrouching(directionalCover.any());
    }

    int playerIndex = player.getIndex();
    float dt = GameTime.instance.getTimeDelta();
    float sneak = 0f;
    float lightfoot = 0f;
    if (player.isSneaking()) {
      IsoCell cell = player.getCell();
      if (cell != null) {
        for (IsoZombie zombie : cell.getZombieList()) {
          if (zombie.getTarget() != player || !zombie.isTargetLocationKnown()) {
            float d = distanceCharacters(player, zombie);

            // from IsoPlayer.updateLOS
            IsoGridSquare c = gimmeSquare(zombie);
            boolean couldSee = c.isCouldSee(playerIndex);
            if (!couldSee && zombie.couldSeeHeadSquare(player)) {
                couldSee = true;
            }
            if (couldSee) {
              sneak += 10f/(10f+d);
            }

            if (player.getMovementLastFrame().getLength() > 0f) {
              lightfoot += 100f/(100f+d*d);
            }
          }
        }
      }
    }

    float flat_mod = .2f;
    if (sneak > 0f) {
      if (sneak > 1f) {
        // sneak is ~ zombie count
        sneak = sqrt(sneak);
      }
      // logf("sneak xp/s", sneak);
      player.getXp().AddXP(PerkFactory.Perks.Sneak, flat_mod*dt*sneak);
    }

    if (lightfoot > 0f) {
      if (lightfoot > 1f) {
        // lightfoot is ~ zombie count
        lightfoot = sqrt(lightfoot);
      }
      // logf("lightfoot xp/s", lightfoot);
      player.getXp().AddXP(PerkFactory.Perks.Lightfoot, flat_mod*dt*lightfoot);
    }
  }

  public static void update_zombie(IsoZombie zombie) {
    if (false) {
      log("--- update zombie");
      log("code %d", zombie.hashCode());
    }
    final Notice notice = getNotice(zombie);

    // log("update zombie");
    final float k1 = kc(l1, t1);
    final float k2 = kc(l2, t2);
    if (zombie.getTarget() != null) {
      notice.v2 = 3.f;
    } else {
      final float k3 = kc(.5f, 30f); // half-life of 30. they stay pissed for a while.
      notice.v2 *= k2;
    }
    notice.v2 = clamp(notice.v2, 0f, 3f);

    for (var entry : notice.forPlayer.entrySet()) {
      IsoPlayer player = entry.getKey();
      NoticePlayer notice_player = entry.getValue();

      // needs v1 to drop *low* to start v2 ticking down from max
      // accelerates quickly after v1+.5v2 is greater than 1.
      //   around v1 = .6 sustained will drive v2
      //   to
      // if v2 > v1, trigger sound or random movement
      // float v2equilibrium = pow(notice.v1+.5f*notice.v2, 2)*notice.v1;
      // notice.v2 += v2equilibrium*(1.f-k2);
      // notice.v2 *= k2;
      // notice.v2 = clamp(notice.v2, 0.f, 3.f);
      // replace with hardcode
      // :note zombie.target gets null'd when TimeSinceSeenFlesh > memory (about 8s)

      notice_player.v1 *= k1;
      notice_player.v1 = clamp01(notice_player.v1);
    }

    update_zombie_sound(zombie);

    // updates run with this
    notice.alertTime -= GameTime.instance.getMultiplier();
    notice.alertTime = max(notice.alertTime, 0f);
  }

  public static List<WorldSoundManager.WorldSound> gimmeSoundList(IsoZombie zombie) {
      WorldSoundManager M = WorldSoundManager.instance;
      IsoGridSquare zc = zombie.getCurrentSquare();
      List<WorldSoundManager.WorldSound> soundList;
      if (zc == null || zc.chunk == null || GameServer.server) {
          soundList = M.soundList;
      } else {
          IsoChunk chunk = zc.chunk;
          soundList = chunk.soundList;
      }
      return soundList;
  }

  public static void update_zombie_sound(IsoZombie zombie) {
      final Notice notice = getNotice(zombie);
      IsoGridSquare zc = gimmeSquare(zombie);

      final float weatherSoundIntensity = weatherSoundIntensityLevel();

      List<WorldSoundManager.WorldSound> soundList = gimmeSoundList(zombie);
      float backgroundSoundIntensity = 0f;
      for (int n = 0; n < soundList.size(); ++n) {
        final WorldSoundManager.WorldSound sound = soundList.get(n);
        final float soundDb = decibelsOfSoundAtPoint(
            sound,
            zombie.getX(),
            zombie.getY(),
            zombie.getZ()
            );

        final float soundIntensity = decibelToIntensity(soundDb);
        backgroundSoundIntensity += soundIntensity;
      }

      // zombies add background sound; could use zombie sounds?
      float zombieSoundIntensity = 0f;
      IsoCell cell = zc.getCell();
      for (var other : cell.getZombieList()) {
        if (other == zombie) {
          continue;
        }
        // quick hack; no room attenuation
        final float r = distanceCharacters(zombie, other);
        final float baseDb = 40f;
        final float distanceAttenuation = -6f*(log2(1.5f*r)-log2(2));
        zombieSoundIntensity += decibelToIntensity(baseDb+distanceAttenuation);
      }

      // some of these are a little redundant, but it doesn't hurt
      float baseline;
      final boolean grid = IsoWorld.instance.isHydroPowerOn();
      final boolean hydro = zc.hasGridPower();
      final boolean powered = !zc.hasGridPower() && zc.haveElectricity();
      if (zc.getRoom() == null) {
        baseline = decibelToIntensity(25 + max(10*ind(hydro), 10*ind(powered), 3*ind(grid)));
      } else {
        baseline = decibelToIntensity(15 + max(15*ind(hydro), 10*ind(powered), 15*ind(grid)));
      }

      float ambientDb = intensityToDecibel(
          baseline
          + weatherSoundIntensity
          + backgroundSoundIntensity
          + zombieSoundIntensity
          );
      float k4 = kc(l4, t4);
      if (notice.v4 == 0f) {
        notice.v4 = ambientDb;
      }
      notice.v4 += (1f-k4)*ambientDb;
      notice.v4 *= k4;

      for (int n = 0; n < soundList.size(); ++n) {
        final WorldSoundManager.WorldSound sound = soundList.get(n);
        final float attract = getSoundAttract(sound,zombie);
        // little hack; irritate zombie on player sounds
        if ((sound.sourceIsPlayer || sound.sourceIsPlayerBase) && attract > 0f) {
          notice.v2 = max(1f, notice.v2);
        }

        // award xp post ambient calculations
        // if (sound.source instanceof IsoPlayer && attract == 0f) {
        //   final IsoPlayer player = (IsoPlayer) sound.source;
        //   if (player.isSneaking() && (zombie.getTarget() != player || !zombie.isTargetLocationKnown())) {
        //     awardLightfootXp(player, sound, zombie);
        //   }
        // }

        if (false) {
          if (sound.source instanceof IsoPlayer) {
            log_always("player sound");
            log_always("  dB:   %f", decibelsOfSoundAtPoint(sound, zombie.getX(), zombie.getY(), zombie.getZ()));
            log_always("  A:    %f", attract);
            log_always("  v4:   %f", notice.v4);
          }
        }
      }

      if (false) {
        log_always("--- update sound");
        log_always("B:      %f", intensityToDecibel(baseline));
        log_always("W:      %f", intensityToDecibel(weatherSoundIntensity));
        log_always("O:      %f", intensityToDecibel(backgroundSoundIntensity));
        log_always("Z:      %f", intensityToDecibel(zombieSoundIntensity));
        log_always("am:     %f", ambientDb);
        log_always("v4:     %f", notice.v4);
      }
  }

  public static void reset_zombie(IsoZombie zombie) {
    if (false) {
      log_always("reset for reuse");
    }
    notice_levels.remove(zombie);
  }

  public static void save_zombie(IsoZombie zombie, java.nio.ByteBuffer buffer) {
    if (false) {
      log_always("save zombie");
    }
  }

  public static void load_zombie(IsoZombie zombie, java.nio.ByteBuffer buffer) {
    if (false) {
      log_always("load zombie");
    }
  }

  public static float zombie_notice_bonus(IsoZombie zombie) {
    float base = 1f;
    final Notice notice = getNotice(zombie);
    return base + notice.v2;
  }

  // this might make zombies a little too prone to hearing random sounds offscreen
  public static float modify_zombie_hearing(IsoZombie zombie, float original) {
    // :note see modify_footstep_addsound for discussion
    return original * sqrt(zombie_notice_bonus(zombie));
  }

  // triangular distribution around 0
  public static int modify_zombie_sound_random_offset(IsoZombie zombie, int hi) {
    // hi is manhattandistance/2.5, so we put a little of the randomness back in
    float hif = 1.5f;
    float a = Rand.Next(-1f, 1f);
    float b = Rand.Next(0f, hif);
    int ret = Math.round(a*b);
    if (false) {
      log_always("zombie sound offset: %d %d %f %f", hi, ret, a, b);
    }
    return ret;
  }

  // public static void inspect_biggest_sound_loop(float delta) {
  //   log_always("loop: radius %f", delta);
  // }

  public static float Iref = 1e-12f;
  public static float intensityToDecibel(float I) {
    return 10*(log10(I) - log10(Iref));
  }
  public static float intensityAtDistance(float P, float r) {
    return P/(4f*((float)Math.PI)*r*r);
  }
  public static float decibelToIntensity(float db) {
    return Iref*pow(10, db/10);
  }
  public static float decibelToLoudness(float db) {
    return pow(2, db/10f);
  }
  public static float decibelOfSound(WorldSoundManager.WorldSound sound) {
    // use whisper as 35dB reference
    // imagine reference distance is around 2/3 of radius and it works out.
    return (35f/log10(6f*6f))*log10(sound.volume*sound.radius);
  }
  public static float soundVolumeOfDecibel(float db) {
    return sqrt(pow(10, (log10(6f*6f)/35f)*db)); // log10(sound.volume*sound.radius);
  }
  // public static float sourcePowerOfSound(WorldSoundManager.WorldSound sound) {
  //   // 4*pi*.66^2 is 5.47
  //   // return 4*Math.PI*pow(0.66f*sound.radius,2)*decibelToIntensity(decibelOfSound(sound))
  //   return 5.47f*pow(sound.radius,2)*decibelToIntensity(decibelOfSound(sound))
  // }

  // reimpl
  private static float plateau(float x) {
      return .333f*(
          x
          +
          1f/(1f+exp(-20f*(x-0.1f)))
          +
          1f/(1f+exp(-20f*(x-0.9f)))
          );
  }
  public static float weatherSoundIntensityLevel() {
      ClimateManager climate = ClimateManager.getInstance();
      float rain = climate.getRainIntensity();
      float rainDecibel = 90f*plateau(rain);
      float rainSoundIntensity = decibelToIntensity(rainDecibel);
      float wind = climate.getWindIntensity();
      float windDecibel = 80f*plateau(wind);
      float windSoundIntensity = decibelToIntensity(windDecibel);
      float weatherSoundIntensity = rainSoundIntensity + windSoundIntensity;
      return weatherSoundIntensity;
  }

  public static float distanceCharacters(IsoGameCharacter c1, IsoGameCharacter c2) {
    dist1.x = c1.getX() - c2.getX();
    dist1.y = c1.getY() - c2.getY();
    dist1.z = 3f*(c1.getZ() - c2.getZ());
    return dist1.getLength();
  }

  public static Vector2 directionCharacters(IsoGameCharacter c1, IsoGameCharacter c2) {
    pzv.x = c2.getX() - c1.getX();
    pzv.y = c2.getY() - c1.getY();
    pzv.normalize();
    return pzv;
  }

  public static float distanceSoundToPoint(WorldSoundManager.WorldSound sound, float zx, float zy, float zz) {
    float sx = (float)sound.x;
    float sy = (float)sound.y;
    float sz = (float)sound.z;
    final Vector3 zsv = zsv3;
    zsv.x = sx - zx;
    zsv.y = sy - zy;
    zsv.z = 3f*sz - 3f*zz; // this is normal
    final float zombieToSoundDistance = zsv.getLength();
    return zombieToSoundDistance;
  }

  public static float decibelsOfSoundAtPoint(WorldSoundManager.WorldSound sound, float zx, float zy, float zz) {
    if (sound == null ||  sound.radius == 0) {
      return 0f;
    }
    int x = (int)zx;
    int y = (int)zy;
    int z = (int)zz;
    final float zombieToSoundDistance = distanceSoundToPoint(sound, zx, zy, zz);

    // 10 dB: Normal breathing
    // 20–29 dB: Light breeze / Rustling leaves
    // 30 dB: Quiet library / Soft whisper
    // 20 to 40 dB: Heavy, labored, or deep breathing, as well as quiet snoring.
    // 50 dB: Moderate rainfall
    // 60 dB: Normal conversation
    final float r = zombieToSoundDistance;
    final float soundReferenceDb = decibelOfSound(sound);
    // assuming reference dB is at 2/3 max radius. adjust as needed.
    final float distanceAttenuation = -6f*(log2(1.5f*max(1,r))-log2(sound.radius));

    // walls block around 30
    float roomAttenuation = 0f;
    IsoCell cell = IsoCell.getInstance();
    if (cell != null) {
      IsoGridSquare sc = cell.getGridSquare(sound.x, sound.y, sound.z);
      IsoGridSquare zc = cell.getGridSquare(x, y, z);
      if (sc != null && zc != null) {
        if (sc.getRoom() == null && zc.getRoom() != null
            ||
            sc.getRoom() != null && zc.getRoom() == null) {
          roomAttenuation = 30f;
        } else if (sc.getRoom() != null && zc.getRoom() != null 
            && zc.getRoom() != zc.getRoom()) {
          roomAttenuation = 50f;
            }
      }
    }
    final float soundDb = soundReferenceDb + distanceAttenuation - roomAttenuation;
    if (false) {
      log_always("sound %s", sound.source != null ? sound.source.getClass().getName() : "null");
      log_always("  r:    %f", (float)sound.radius);
      log_always("  v:    %f", (float)sound.volume);
      log_always("  D:    %f", zombieToSoundDistance);
      log_always("  dbR:  %f", soundReferenceDb);
      log_always("  aD:   %f", distanceAttenuation);
      log_always("  aX:   %f", roomAttenuation);
      log_always("  db:   %f", soundDb);
    }
    return soundDb;
  }

  public static WorldSoundManager.ResultBiggestSound resultBiggestSound;
  static {
    resultBiggestSound = new WorldSoundManager.ResultBiggestSound();
  }
  public static WorldSoundManager.ResultBiggestSound getBiggestSoundZomb(IsoZombie zombie, int x, int y, int z) {
      // if (zombie != null) {
      final Notice notice = getNotice(zombie);
      List<WorldSoundManager.WorldSound> soundList = gimmeSoundList(zombie);

      WorldSoundManager.WorldSound biggestSound = null;
      float biggestAttract = 0f;
      for (int n = 0; n < soundList.size(); ++n) {
        final WorldSoundManager.WorldSound sound = soundList.get(n);
        final float attract = getSoundAttract(sound, zombie);
        if (false) {
          log_always("biggest sound?");
          log_always("sound %s", sound.source != null ? sound.source.getClass().getName() : "null");
          log_always("  r:    %f", (float)sound.radius);
          log_always("  v:    %f", (float)sound.volume);
          log_always("  A:    %f", attract);
        }
        if (attract > biggestAttract) {
          biggestAttract = attract;
          biggestSound = sound;
        }
      }

      return Patch.resultBiggestSound.init(biggestSound, biggestAttract);
  }

  public static float getSoundAttract(WorldSoundManager.WorldSound sound, IsoZombie zombie) {
    // :note
    // getSoundZomb looks for a sound with zom.soundSourceTarget == sound.source && sound.stressZombies,
    //   so they'll already prioritize following a players footsteps without help.
    if (sound == null) {
      return 0f;
    }

    // ignore distance is weird; only zombies beyond that distance are attracted
    final float zombieToSoundDistance = distanceSoundToPoint(sound, zombie.getX(), zombie.getY(), zombie.getZ());
    if (!sound.stressZombies || sound.sourceIsZombie || zombieToSoundDistance < sound.zombieIgnoreDist) {
      return 0f;
    }

    final float soundDb = decibelsOfSoundAtPoint(
        sound,
        zombie.getX(),
        zombie.getY(),
        zombie.getZ()
        );

    final Notice notice = getNotice(zombie);
    final float threshold = notice.v4 - 12f; // v4 is average ambient noise
    if (soundDb < threshold) {
      return 0f;
    }

    // between band, maybe don't respond
    // doesn't work due to repeat polling
    // if (soundDb < notice.v4 + 15f) {
    //   if ((soundDb-notice.v4)/15f < Rand.Next(-1f, 1f)) {
    //     return 0f;
    //   }
    // }

    // roughly, loudness. 40 as baseline.
    // RespondToSound uses 2x as threshold to ditch one sound for another, so 10db difference.
    float attract = pow(2f, (soundDb-40f)/10f);
    attract *= 1f + ind(sound.source == zombie.getTarget()); // todo
    return attract;
  }


  // public static void zombie_biggest_sound(IsoZombie zombie, zombie.WorldSoundManager.ResultBiggestSound original) {
  //   if (original.sound == null) {
  //     // log_always("biggest sound: null");
  //     return;
  //   }
  //   // log_always(
  //   //     "biggest sound: %d %d (%d %d %d)",
  //   //     original.sound.radius,
  //   //     original.sound.volume,
  //   //     original.sound.x,
  //   //     original.sound.y,
  //   //     original.sound.z
  //   //     );
  //   WorldSoundManager.WorldSound sound = original.sound;
  //   if (sound.source instanceof IsoPlayer) {
  //     IsoPlayer player = (IsoPlayer) sound.source;
  //     final Notice notice = getNoticeForPlayer(zombie, player);
  //     notice.v3 += 1f; // adjust for distance
  //   }

  //   if (sound.sourceIsPlayer || sound.sourceIsPlayerBase) {
  //     // may trigger multiple times per sound (0-2?)
  //     //   would be nice to normalize, kc is place holder
  //     //   could arm something and apply in update_zombie instead
  //     //
  //     // 4 is footstep sneaking at 0 sneak
  //     // 6 is whisper
  //     // 7 is footstep walking
  //     // 11 is footstep jogging
  //     // 14 is footstep sprinting
  //     // 30 is yell
  //     //
  //     // this still requires tweaking
  //     final float irritation =
  //       kc(.9f, .3f)
  //       // * .5f // reduce from base hearing multiplier bonus
  //       * WorldSoundManager.instance.getHearingMultiplier(zombie)
  //       * clamp01(pow(sound.volume/30f, 1.5f));
  //     if (true) {
  //       log_always("biggest sound was player base. irritating!");
  //       log_always("  volume:     %d",   sound.volume);
  //       log_always("  irritation: %.2f", irritation);
  //     }
  //     for (var entry : getNotice(zombie).entrySet()) {
  //       final Notice notice = entry.getValue();
  //       notice.v2 += irritation;
  //       notice.v2 = clamp(notice.v2, 0, 3);
  //     }
  //   }
  // }

  public static float modToDb(float x) {
    return 3f*log2(x);
  }
  public static WorldSoundManager.WorldSound modify_footstep_addsound(float original_volume, WorldSoundManager instance, Object source, int x, int y, int z, int radius, int volume, boolean stressHumans, float zombieIgnoreDist, float stressMod, boolean sourceIsZombie, boolean doSend, boolean remote) {
    if (!(source instanceof IsoPlayer)) {
      return instance.addSound(source, x, y, z, radius, volume, stressHumans, zombieIgnoreDist, stressMod, sourceIsZombie, doSend, remote);
    }
    IsoPlayer player = (IsoPlayer) source;
    // original_volume is from movement
    // case "sneak_walk": {
    //     speed = 0.25f;
    // case "sneak_run": {
    //     speed = 0.25f;
    // case "strafe": {
    //     speed = 0.5f;
    // case "walk": {
    //     speed = 0.5f;
    // case "run": {
    //     speed = 0.75f;
    // case "sprint": {
    //     speed = 1.0f;

    // base
    float baseDb = 40f;
    // final float baseVolume = 10f;
    // float V = baseVolume;

    // non-linear approximates the r-squared relation, since we can't handle it here (no zombie)
    if (player.isSneaking() || player.isAiming()) {
      baseDb -= 10f;
    }
    if (player.isRunning()) {
      baseDb += 5f;
    }
    if (player.isSprinting()) {
      baseDb += 10f;
    }

    // bodyweight
    // 50 emaciated
    // 80 default
    // 105 obese
    final float bodyweight = (float) player.getNutrition().getWeight();
    final float bodyweightMod = bodyweight / 80f;
    // final float bodyweightDb = 5f*((bodyweight-80f)/80f);

    // encumberance
    final float weight = player.getInventory().getCapacityWeight();
    final float weightMod = (20f + weight)/25f;
    // final float weightDb = 5f*((sqrt(weight)-sqrt(10f))/sqrt(10f));
    // final float maxWeight = (float)player.getMaxWeight();

    // indoors
    // float indoorsMod = (player.getCurrentSquare().getRoom() != null) ? .5f : 1f;
    
    // grass or not
    boolean isGrass = player.getCurrentSquare().hasGrassLike();

    // skills
    // vanilla lightfoot mod from .99 to .2
    // ours from 1 to .16
    final int lightfootLevel = player.getPerkLevel(PerkFactory.Perks.Lightfoot);
    final float lightfootLevelX = lightfootLevel + 2f*ind(player.isSneaking());
    float lightfoot = pow(1f - .05f*lightfootLevelX, 2f);

    // vanilla nimble mod from 1 to 1.5 (1 to .5)
    // ours from 1 to .65
    final int nimbleLevel = player.getPerkLevel(PerkFactory.Perks.Nimble);
    float nimble = pow(1f - .025f*nimbleLevel, 1.5f);

    // vanilla sneak mod from 1.2ish to .48
    // ours from 1 to .58
    final int sneakLevel = player.getPerkLevel(PerkFactory.Perks.Sneak);
    final float sneakLevelX = sneakLevel + 2f*ind(player.isSneaking());
    float sneak = pow(1f - .025f*sneakLevelX, 1.5f);

    // shoes
    float shoemod = 1f;
    float noshoesmod = .7f;
    Clothing shoes = (Clothing) player.getWornItem(ItemBodyLocation.SHOES);
    if (shoes == null) {
      shoemod = noshoesmod;
    } else {
      // footwraps weight .2
      // most other weight 1
      // stomp power ranges from
      //   .8 slippers
      //   1.8 sneakers
      //   2.1 shoes
      //   2.5 boots
      //
      // grass is less punishing on footwear
      shoemod = noshoesmod + (isGrass ? .5f : 1f) * shoes.getWeight() * pow(shoes.getStompPower()/2.1f, 2);
    }

    float V = 1f;
    V *= bodyweightMod;
    V *= weightMod;
    V *= lightfoot;
    V *= nimble;
    V *= sneak;
    V *= shoemod;
    // more consistent w/ level, graceful, clumsy
    boolean graceful = player.hasTrait(CharacterTrait.GRACEFUL);
    boolean clumsy = player.hasTrait(CharacterTrait.CLUMSY);
    V *= graceful ? 0.8f : 1f;
    V *= clumsy ? 1.2f : 1f;
    float Vdb = modToDb(V);
    float hi = graceful ? 3f : clumsy ? 8f : 5f;
    float randomDb = Rand.Next(0f, hi);
    float db = baseDb + Vdb + randomDb;
    float vol = soundVolumeOfDecibel(db);

    // try to preserve dB through integerization
    int rad = (int) Math.round(vol);
    int dr = 0;
    float d0 = abs(rad*rad - vol*vol);
    float d1 = abs(rad*(rad+1) - vol*vol);
    float d2 = abs(rad*(rad-1) - vol*vol);
    if (d1 < d0 && d1 <= d2) {
      dr = 1;
    } else if (d2 < d0 && d2 <= d1) {
      dr = -1;
    }

    // return
    if (false) {
      log_always("footstep:");
      log_always("  B:   %f", baseDb);
      log_always("  V:   %f", V);
      log_always("  Vdb: %f", Vdb);
      log_always("  db:  %f", db);
      log_always("  vol: %f", vol);
      log_always("  rad: %d", rad);
      log_always("  dr:  %d", dr);
    }

    return instance.addSound(source, x, y, z, rad, rad+dr, stressHumans, zombieIgnoreDist, stressMod, sourceIsZombie, doSend, remote);
  }

  // ENV
  // can't use RenderSettings.ambient directly because night vision and such affects it
  private static float actualAmbient(int playerIndex) {
    // from RenderSettings
    //
    // this.ambient = this.cmAmbient;
    // this.viewDistance = this.cmViewDistance;
    // final int sv = SandboxOptions.instance.nightDarkness.getValue();
    // float ambientMin = switch (sv) {
    //     case 1 -> 0.0f;
    //     case 2 -> 0.07f;
    //     case 3 -> 0.15f;
    //     case 4 -> 0.25f;
    //     default -> 0.15f;
    // };
    // ambientMin += 0.075f * ClimateMoon.getInstance().getMoonFloat() * this.night;
    // if (!this.isExterior) {
    //     ambientMin *= 0.925f - 0.075f * this.darkness;
    //     this.desaturation *= 0.25f;
    // }
    // if (this.ambient < 0.2f && player != null && player.hasTrait(CharacterTrait.NIGHT_VISION)) {
    //     this.ambient = 0.2f;
    // }
    // this.ambient = ambientMin + (1.0f - ambientMin) * this.ambient;
    // if (Core.lastStand) {
    //     this.ambient = 0.65f;
    //     this.darkness = 0.25f;
    //     this.night = 0.25f;
    // }
    final ClimateManager climate = ClimateManager.getInstance();
    final RenderSettings.PlayerRenderSettings render = RenderSettings.getInstance().getPlayerSettings(playerIndex);

    final float climateAmbient = climate.getAmbient();
    final int darknessOption = SandboxOptions.instance.nightDarkness.getValue();
    float base = switch (darknessOption) {
        case 1 -> 0.0f;
        case 2 -> 0.07f;
        case 3 -> 0.15f;
        case 4 -> 0.25f;
        default -> 0.15f;
    };
    final float night = climate.getNightStrength();
    final float moon_strength = ClimateMoon.getInstance().getMoonFloat() * night;
    final float moon_light = .075f * moon_strength;

    final float darkness = 1f - climate.getDayLightStrength();
    final float interior = render.isExterior() ? 1f : .925f - .075f * darkness;
    final float ambient_min = (base+moon_light)*interior;
    final float ambient = ambient_min + (1f - ambient_min)*climateAmbient;
    return ambient;
  }

  // try really hard to get a square
  public static IsoGridSquare gimmeSquare(IsoGameCharacter C) {
      IsoGridSquare c = C.getCurrentSquare();
      if (c == null) {
        c = IsoCell.getInstance().getGridSquare(
            (int)C.getX(),
            (int)C.getY(),
            (int)C.getZ()
            );
      }
      if (c == null) {
        c = IsoCell.getInstance().getGridSquare(
            (int)C.getX(),
            (int)C.getY(),
            0
            );
      }
      return c;
  }

  public static float squareLightLevel(IsoGridSquare square) {
    return squareLightLevel(square, 0);
  }

  public static float squareLightLevel(IsoGridSquare square, int playerIndex) {
    final ColorInfo color = square.lighting[playerIndex].lightInfo();
    final float light = (color.r + color.g + color.b) / 3.0f;
    return light;
  }

  public static float lightLevelRaw(IsoGameCharacter player) {
    int playerIndex = 0;
    if (player instanceof IsoPlayer) {
      playerIndex = ((IsoPlayer) player).getPlayerIndex();
    }
    IsoGridSquare c = gimmeSquare(player);
    // ambient isn't necessary anymore
    // final float actualAmbientLight = actualAmbient(playerIndex);
    final float squareLight = squareLightLevel(c, playerIndex);

    // .18 is pretty dark
    // .8 is pretty bright
    // above .8 no bonuses, here
    // stretch a little
    final float playerLight = pow(clamp01((squareLight)/.8f), 1.1f); // empirical, don't wiggle too much
    return playerLight;
  }

  public static float lightLevelWithTorch(IsoGameCharacter player) {
    final float torchLight = clamp01(player.getTorchStrength()/2f);
    final float playerLight = max(torchLight, lightLevel(player));
    return playerLight;
  }

  public static float light_floor = 0f;
  public static float lightLevel(IsoGameCharacter player) {
    return max(lightLevelRaw(player), light_floor);
  }

  public static float weatherNoiseLevel(IsoGameCharacter player) {
    final boolean isOutside = player.getSquare().getRoom() == null;
    if (!isOutside) {
      return 0f;
    }
    final ClimateManager climate = ClimateManager.getInstance();
    final float fogIntensity = climate.getFogIntensity();
    final float rainIntensity = climate.getRainIntensity();
    final float snowIntensity = climate.getSnowIntensity();
    final float windIntensity = climate.getWindIntensity();
    final float weatherNoise =
        0f
        + fogIntensity*6.f
        + snowIntensity*2.f
        + rainIntensity*1.f
        + windIntensity*.5f
      ;
    return weatherNoise;
  }

  public static float playerSneakLevelFactor(IsoPlayer player) {
    final boolean isSneaking = player.isSneaking();
    final int playerSneakLevel = player.getPerkLevel(PerkFactory.Perks.Sneak);
    final float playerSneakSpotMod = player.getSneakSpotMod();
    final float playerSneakX = .1f*playerSneakLevel + .2f * ind(isSneaking);
    return playerSneakX;
  }

  public static float playerVisibilityFromLight(IsoPlayer player) {
    final float playerSneakX = playerSneakLevelFactor(player);
    final float playerLight = lightLevelWithTorch(player);
    final float visibilityFromLight = pow(playerLight, 1.f+0.15f*playerSneakX);
    return visibilityFromLight;
  }

  public static float playerVisibilityFromMovement(IsoPlayer player) {
    final float playerSneakX = playerSneakLevelFactor(player);
    final boolean isSneaking = player.isSneaking();
    final boolean isAttacking = player.isAttacking();
    final boolean isPerformingAction = player.isPerformingAnAction();
    final float playerMovementLastFrame = player.getMovementLastFrame().getLength();
    final float visibilityFromMovement =
      (
       1.f
        +
        (
        2f*playerMovementLastFrame // movement is .5 when sneaking
        + 2f*ind(isPerformingAction)
        + 5f*ind(isAttacking)
        )
        *
        (isSneaking ? 1f/(1f+playerSneakX) : 1f)
        // + ind(isConspicuous)*.5f // :consider making Conspicuous/Inconspicuous only modify "movements"
        // + ind(isRunning)*.5f
        // + ind(isSprinting)
      )
      ;
    return visibilityFromMovement;
  }

  public static float playerVisibility(IsoPlayer player) {
    if (false) {
      return 0f;
    }

    // player predicates
    final boolean isSneaking = player.isSneaking();
    final boolean isConspicuous = player.hasTrait(CharacterTrait.CONSPICUOUS);
    final boolean isInconspicuous = player.hasTrait(CharacterTrait.INCONSPICUOUS);

    // player sneak
    final float playerSneakX = playerSneakLevelFactor(player);

    //
    // torch light strength range from
    //   .6  disposable lighter
    //   2.0 torch
    // torch light distance range from
    //   5 disposable lighter
    //   25 torch
    // player move
    final float visibilityFromLight = playerVisibilityFromLight(player);
    final float visibilityFromMovement = playerVisibilityFromMovement(player);
    final float visibilityNoCover =
      visibilityFromLight
      *
      visibilityFromMovement
      *
      (1.f+ind(isConspicuous)*.2f)
      *
      (1.f-ind(isInconspicuous)*.2f)
      *
      (1.f-ind(isSneaking)*.1f) // very small; sneaking mostly factors into other things
      ;

    if (false) {
      log("..");
      logf("visibility", visibilityNoCover);
      logf("  light", lightLevel(player));
      logf("  light v", visibilityFromLight);
      logf("  movement", visibilityFromMovement);
    }
    return visibilityNoCover;
  }

  public static float zombieAcuityAtPlayerLocation(IsoZombie zombie, IsoPlayer player) {
    final Notice notice = getNotice(zombie);
    final NoticePlayer notice_player = getNoticeForPlayer(zombie, player);
    final Vector2 v1 = pzv;
    v1.x = player.getX() - zombie.getX();
    v1.y = player.getY() - zombie.getY();
    final float zombieToPlayerDistance = v1.getLength();
    v1.normalize();
    final Vector2 v2 = zlv;
    zombie.getLookVector(v2);
    final float lookAngleDot = v2.dot(v1);
    // :note they can see a full 360 when aggroed (looking around)
    final float lookAngleExtentPast180 = .5f + clamp01(notice.v2); // sin(Math.PI/6);
    final float L = clamp01((lookAngleDot + lookAngleExtentPast180)/(1f+lookAngleExtentPast180));
    final float acuityAtAngle = sqrt(L); // L*L

    // undo part of the player light visibility bonus;
    // playerLight is always greater than playerVisibilityFromLight
    final float playerLight = lightLevelWithTorch(player);
    final float zombieLight = lightLevel(zombie);
    // so, .05 to 20ish
    float lightLevelDifferenceAcuity = ((.05f+playerLight)/(.05f+zombieLight));

    // acuity
    final float noticeAcuityBonus = (1.f + notice.v2); // 1,4
    final float distancePower = 1.5f;
    // want this to be ~50 when standing right next to player
    final float acuityAtDistance = pow(30f / (1f+zombieToPlayerDistance), distancePower);
    final float acuity =
      acuityAtDistance
      *
      acuityAtAngle
      *
      noticeAcuityBonus
      *
      lightLevelDifferenceAcuity
      ;

    if (false) {
      log("..");
      logf("acuity", acuity);
      logf("  distance", zombieToPlayerDistance);
      logf("  acuity at distance", acuityAtDistance);
      logf("  acuity at angle", acuityAtAngle);
      logf("    dot", lookAngleDot);
      logf("    L", L);
      logf("  irritation bonus", noticeAcuityBonus);
      logf("  light level delta", lightLevelDifferenceAcuity);
    }
    return acuity;
  }


  // NEARWALL

  private static class DCover {
    public float N;
    public float W;
    public float S;
    public float E;

    public float NW;
    public float NE;
    public float SW;
    public float SE;
    public DCover() {
      this.N = 0f;
      this.W = 0f;
      this.S = 0f;
      this.E = 0f;

      this.NW = 0f;
      this.NE = 0f;
      this.SW = 0f;
      this.SE = 0f;
    }

    public boolean any() {
      return (
        this.N != 0f ||
        this.W != 0f ||
        this.S != 0f ||
        this.E != 0f ||

        this.NW != 0f ||
        this.NE != 0f ||
        this.SW != 0f ||
        this.SE != 0f
      );
    }

    public void reset() {
      this.N = 0f;
      this.W = 0f;
      this.S = 0f;
      this.E = 0f;

      this.NW = 0f;
      this.NE = 0f;
      this.SW = 0f;
      this.SE = 0f;
    }
  }
  public static int bN = 1<<0;
  public static int bW = 1<<1;
  public static int bS = 1<<2;
  public static int bE = 1<<3;
  public static int directionalCoverFlag(final IsoPlayer player) {
    final IsoGridSquare c = player.getCurrentSquare();
    if (c == null) {
      return 0;
    }

    final boolean north = true;
    final boolean west = false;

    final IsoGridSquare cn = c.getAdjacentSquare(IsoDirections.N);
    final IsoGridSquare cs = c.getAdjacentSquare(IsoDirections.S);
    final IsoGridSquare ce = c.getAdjacentSquare(IsoDirections.E);
    final IsoGridSquare cw = c.getAdjacentSquare(IsoDirections.W);

    final IsoGridSquare cne = c.getAdjacentSquare(IsoDirections.NE);
    final IsoGridSquare cnw = c.getAdjacentSquare(IsoDirections.NW);
    final IsoGridSquare cse = c.getAdjacentSquare(IsoDirections.SE);
    final IsoGridSquare csw = c.getAdjacentSquare(IsoDirections.SW);

    // properties
    final PropertyContainer p0 = getProperties(c);

    final PropertyContainer pn = getProperties(cn);
    final PropertyContainer ps = getProperties(cs);
    final PropertyContainer pe = getProperties(ce);
    final PropertyContainer pw = getProperties(cw);

    final PropertyContainer pne = getProperties(cne);
    final PropertyContainer pnw = getProperties(cnw);
    final PropertyContainer pse = getProperties(cse);
    final PropertyContainer psw = getProperties(csw);

    //
    int ret = 0;
    if (blocksN(p0) || blocks(pn) || blocks(pne) || blocks(pnw)) {
      ret |= bN;
    }

    if (blocksW(p0) || blocks(pw) || blocks(psw) || blocks(pnw)) {
      ret |= bW;
    }

    if (blocksN(ps) || blocks(ps) || blocks(pse) || blocks(psw)) {
      ret |= bS;
    }

    if (blocksW(pe) || blocks(pe) || blocks(pse) || blocks(pne)) {
      ret |= bE;
    }

    return ret;
  }

  public static DCover updateDirectionalCover(final IsoPlayer player) {
    final DCover cover = directionalCoverFor(player);
    cover.reset();
    final IsoGridSquare c = player.getCurrentSquare();
    if (c == null) {
      return cover;
    }

    final boolean north = true;
    final boolean west = false;

    final IsoGridSquare cn = c.getAdjacentSquare(IsoDirections.N);
    final IsoGridSquare cs = c.getAdjacentSquare(IsoDirections.S);
    final IsoGridSquare ce = c.getAdjacentSquare(IsoDirections.E);
    final IsoGridSquare cw = c.getAdjacentSquare(IsoDirections.W);

    final IsoGridSquare cne = c.getAdjacentSquare(IsoDirections.NE);
    final IsoGridSquare cnw = c.getAdjacentSquare(IsoDirections.NW);
    final IsoGridSquare cse = c.getAdjacentSquare(IsoDirections.SE);
    final IsoGridSquare csw = c.getAdjacentSquare(IsoDirections.SW);

    // properties
    final PropertyContainer p0 = getProperties(c);

    final PropertyContainer pn = getProperties(cn);
    final PropertyContainer ps = getProperties(cs);
    final PropertyContainer pe = getProperties(ce);
    final PropertyContainer pw = getProperties(cw);

    final PropertyContainer pne = getProperties(cne);
    final PropertyContainer pnw = getProperties(cnw);
    final PropertyContainer pse = getProperties(cse);
    final PropertyContainer psw = getProperties(csw);

    //
    int ret = 0;
    if (blocksN(p0) || blocks(pn)) {
      cover.N = 1f;
    }

    if (blocksW(p0) || blocks(pw)) {
      cover.W = 1f;
    }

    if (blocksN(ps) || blocks(ps)) {
      cover.S = 1f;
    }

    if (blocksW(pe) || blocks(pe)) {
      cover.E = 1f;
    }

    //
    if (blocksN(pw) || blocksW(pn) || blocks(pnw)) {
      cover.NW = 1f;
    }

    if (blocksN(pe) || blocksW(pne) || blocks(pne)) {
      cover.NE = 1f;
    }

    if (blocksN(psw) || blocksW(ps) || blocks(psw)) {
      cover.SW = 1f;
    }

    if (blocksN(pse) || blocksW(pse) || blocks(pse)) {
      cover.SE = 1f;
    }

    return cover;
  }

  public static PropertyContainer getProperties(IsoGridSquare c) {
    if (c == null) {
      return null;
    }
    return c.getProperties();
  }

  public static boolean propertiesHas(PropertyContainer p, IsoFlagType... flags) {
    if (p == null) {
      return false;
    }
    for (IsoFlagType flag : flags) {
      if (p.has(flag)) {
        return true;
      }
    }
    return false;
  }

  public static boolean blocksN(PropertyContainer p) {
    return propertiesHas(p, IsoFlagType.collideN, IsoFlagType.WindowN, IsoFlagType.doorN, IsoFlagType.HoppableN, IsoFlagType.TallHoppableN);
  }

  public static boolean blocksW(PropertyContainer p) {
    return propertiesHas(p, IsoFlagType.collideW, IsoFlagType.WindowW, IsoFlagType.doorW, IsoFlagType.HoppableW, IsoFlagType.TallHoppableW);
  }

  public static boolean blocks(PropertyContainer p) {
    return propertiesHas(
        p,
        IsoFlagType.solid,
        IsoFlagType.blocksight,
        // "CloseSneakBonus",
        IsoFlagType.solidtrans
        );
  }

  // its private in IsoDiretions
  // public static IsoDirections[] directions;
  // static {
  //   directions = new IsoDirections[8]
  //   for (int i=0; i<8; i++) {
  //     directions[i] = IsoDirections.fromIndex(i);
  //   }
  // }
  public static float nearWallFactor(final IsoPlayer player, final IsoZombie zombie) {
    final DCover dcover = directionalCoverFor(player);
    final Vector2 pzv = directionCharacters(player, zombie);
    final float angle = pzv.getDirection();
    logf("angle", angle);
    final IsoDirections direction = IsoDirections.fromAngle(angle);
    log("drxn: %s", direction);
    switch (direction) {
      case IsoDirections.N:
        return dcover.N;
      case IsoDirections.NW:
        return dcover.NW;
      case IsoDirections.W:
        return dcover.W;
      case IsoDirections.SW:
        return dcover.SW;
      case IsoDirections.S:
        return dcover.S;
      case IsoDirections.SE:
        return dcover.SE;
      case IsoDirections.E:
        return dcover.E;
      case IsoDirections.NE:
        return dcover.NE;
    }
    return 0f;
  }
 
  // returns 0,1
  public static float nearVehicleFactor(IsoPlayer player, IsoZombie zombie) {
    // min(8f, 8f*adjbonusvehicle(player, zombie)),
    float mod = 0.f;
    final Vector3f start = BaseVehicle.allocVector3f();
    final Vector3f end = BaseVehicle.allocVector3f();
    final Vector3f intersect1 = BaseVehicle.allocVector3f();
    for (final BaseVehicle vehicle : IsoWorld.instance.currentCell.getVehicles()) {
      // inverse of IsoGameCharacter.isNearVehicle
      if (vehicle.DistTo(player) >= 3.5f) {
          continue;
      }

      start.set(zombie.getX(), zombie.getY(), zombie.getZ() + 0.1f);
      end.set(player.getX(), player.getY(), player.getZ() + 0.1f);
      Vector3f intersect2 = vehicle.getIntersectPoint(start, end, intersect1);
      if (intersect2 != null) {
        mod += 0.25f;
      }

      end.z += 0.3f;
      intersect2 = vehicle.getIntersectPoint(start, end, intersect1);
      if (intersect2 != null) {
        mod += 0.25f;
      }
      end.z -= 0.3f;

      // :todo just rotate and add
      end.x -= 0.3f;
      intersect2 = vehicle.getIntersectPoint(start, end, intersect1);
      if (intersect2 != null) {
        mod += 0.125f;
      }
      end.x += 0.3f;

      end.x += 0.3f;
      intersect2 = vehicle.getIntersectPoint(start, end, intersect1);
      if (intersect2 != null) {
        mod += 0.125f;
      }
      end.x -= 0.3f;

      end.y -= 0.3f;
      intersect2 = vehicle.getIntersectPoint(start, end, intersect1);
      if (intersect2 != null) {
        mod += 0.125f;
      }
      end.y += 0.3f;

      end.y += 0.3f;
      intersect2 = vehicle.getIntersectPoint(start, end, intersect1);
      if (intersect2 != null) {
        mod += 0.125f;
      }
      end.y -= 0.3f;
    }
    BaseVehicle.releaseVector3f(start);
    BaseVehicle.releaseVector3f(end);
    BaseVehicle.releaseVector3f(intersect1);
    // logf("vehiclemod", mod);
    return mod;
  }


  // :todo this is overly generous and a little wonky with determining if adjacent
  //       features are aligned with zombie sight, but better than vanilla.
  public static float nearWallFactor_old(final IsoPlayer player, final IsoZombie zombie) {
    final IsoGridSquare c = player.getCurrentSquare();
    if (c == null) {
      return 0.f;
    }

    // initialize - used by adjbonus
    pzv.x = zombie.getX() - player.getX();
    pzv.y = zombie.getY() - player.getY();
    pzv.normalize();

    // sugar
    final boolean north = true;
    final boolean west = false;

    final IsoGridSquare cn = c.getAdjacentSquare(IsoDirections.N);
    final IsoGridSquare cs = c.getAdjacentSquare(IsoDirections.S);
    final IsoGridSquare ce = c.getAdjacentSquare(IsoDirections.E);
    final IsoGridSquare cw = c.getAdjacentSquare(IsoDirections.W);

    final IsoGridSquare cne = c.getAdjacentSquare(IsoDirections.NE);
    final IsoGridSquare cnw = c.getAdjacentSquare(IsoDirections.NW);
    final IsoGridSquare cse = c.getAdjacentSquare(IsoDirections.SE);
    final IsoGridSquare csw = c.getAdjacentSquare(IsoDirections.SW);

    return max(
      // walls
      adjbonuswall(player, zombie, c, north),
      adjbonuswall(player, zombie, c, west),
      adjbonuswall(player, zombie, cs, north),
      adjbonuswall(player, zombie, ce, west),

      adjbonuswall(player, zombie, cw, north),
      adjbonuswall(player, zombie, ce, north),
      adjbonuswall(player, zombie, csw, north),
      adjbonuswall(player, zombie, cse, north),

      adjbonuswall(player, zombie, cn, west),
      adjbonuswall(player, zombie, cs, west),
      adjbonuswall(player, zombie, cne, west),
      adjbonuswall(player, zombie, cse, west),

      // solidtrans and close sneak
      adjbonussimp(player, c, 0.5f),

      adjbonussimp(player, cn, 0.f),
      adjbonussimp(player, cs, 0.f),
      adjbonussimp(player, ce, 0.f),
      adjbonussimp(player, cw, 0.f),

      adjbonussimp(player, cne, 0.f),
      adjbonussimp(player, cnw, 0.f),
      adjbonussimp(player, cse, 0.f),
      adjbonussimp(player, csw, 0.f),

      // vehicle
      min(8f, 8f*adjbonusvehicle(player, zombie)),
      0.f
    );
  }

  public static float adjbonuswall(final IsoPlayer player, final IsoZombie zombie, final IsoGridSquare c, final boolean isNorth) {
    float result = adjbonuswall_nolog(player, zombie, c, isNorth);
    // logf("adjbonuswall", result);
    return result;
  }

  // :todo .has has a variadic variant that or's flags together
  public static float adjbonuswall_nolog(final IsoPlayer player, final IsoZombie zombie, final IsoGridSquare c, final boolean isNorth) {
    if (c == null) {
      return 0.f;
    }
    // :todo take advantage of all the flags in IsoFlagType to differentiate cover
    //
    // for example, WallTrans vs. Wall vs. Hoppable
    //   ? cutN
    final float bonus = 8.0f;
    if (isNorth) {
      if (c.getProperties().has(IsoFlagType.collideN) || c.getProperties().has(IsoFlagType.WindowN) || c.getProperties().has(IsoFlagType.doorN)) {
        closestPointLine(player.getX(), player.getY(), c.x, c.y, c.x+1.0f, c.y, closest2);
        // line(player.getX(), player.getY(), player.z, closest2.x, closest2.y);
        return bonus * alignment(player, closest2.x, closest2.y);
      }
    } else { // iswest
      if (c.getProperties().has(IsoFlagType.collideW) || c.getProperties().has(IsoFlagType.WindowW) || c.getProperties().has(IsoFlagType.doorW)) {
        closestPointLine(player.getX(), player.getY(), c.x, c.y, c.x, c.y+1.0f, closest2);
        // line(player.getX(), player.getY(), player.z, closest2.x, closest2.y);
        return bonus * alignment(player, closest2.x, closest2.y);
      }
    }
    return 0.0f;
  }

  public static float adjbonussimp(final IsoPlayer player, final IsoGridSquare c, final float dotfudge) {
    float result = adjbonussimp_nolog(player, c, dotfudge);
    // logf("adjbonussimp", result);
    return result;
  }

  public static float adjbonussimp_nolog(final IsoPlayer player, final IsoGridSquare c, final float dotfudge) {
    if (c == null) {
      return 0.f;
    }

    float bonus = 0.0f;
    if (c.getProperties().has(IsoFlagType.solidtrans)) {
      final float _bonus = 6.0f;
      bonus = bonus > _bonus ? bonus : _bonus;
    }
    if (c.getProperties().has(IsoFlagType.solid)) {
      final float _bonus = 6.0f;
      bonus = bonus > _bonus ? bonus : _bonus;
    }
    // :todo this doesn't seem to actually check if any objects blocksight...?
    if (c.getProperties().has(IsoFlagType.blocksight)) {
      final float _bonus = 6.0f;
      bonus = bonus > _bonus ? bonus : _bonus;
    }
    if (c.getProperties().has("CloseSneakBonus")) {
      final float _bonus = Integer.parseInt(c.getProperties().get("CloseSneakBonus")) / 100.0f;
      bonus = bonus > _bonus ? bonus : _bonus;
    }
    if (bonus > 0.0f) {
      final float align = alignment(player, c.x+0.5f, c.y+0.5f, dotfudge);
      if (align > 0.0) {
        // line(player.getX(), player.getY(), player.z, c.x+0.5f, c.y+0.5f);
        return bonus * align;
      }
    }
    return 0.0f;
  }

  // ripped from IsoZombie
  // redo to make fuzzy by computing if two points left/right of player center are obscured
  // redo to use loadNearishVehicles?
  // :todo this isn't perfectly tuned, but its better than vanilla!
  private static float adjbonusvehicle(IsoPlayer player, IsoZombie zombie) {
    float mod = 0.f;
    final Vector3f start = BaseVehicle.allocVector3f();
    final Vector3f end = BaseVehicle.allocVector3f();
    final Vector3f intersect1 = BaseVehicle.allocVector3f();
    for (final BaseVehicle vehicle : IsoWorld.instance.currentCell.getVehicles()) {
      start.set(zombie.getX(), zombie.getY(), zombie.getZ() + 0.1f);
      end.set(player.getX(), player.getY(), player.getZ() + 0.1f);
      Vector3f intersect2 = vehicle.getIntersectPoint(start, end, intersect1);
      if (intersect2 != null) {
        mod += 0.25f;
      }

      end.z += 0.3f;
      intersect2 = vehicle.getIntersectPoint(start, end, intersect1);
      if (intersect2 != null) {
        mod += 0.25f;
      }
      end.z -= 0.3f;

      // :todo just rotate and add
      end.x -= 0.3f;
      intersect2 = vehicle.getIntersectPoint(start, end, intersect1);
      if (intersect2 != null) {
        mod += 0.125f;
      }
      end.x += 0.3f;

      end.x += 0.3f;
      intersect2 = vehicle.getIntersectPoint(start, end, intersect1);
      if (intersect2 != null) {
        mod += 0.125f;
      }
      end.x -= 0.3f;

      end.y -= 0.3f;
      intersect2 = vehicle.getIntersectPoint(start, end, intersect1);
      if (intersect2 != null) {
        mod += 0.125f;
      }
      end.y += 0.3f;

      end.y += 0.3f;
      intersect2 = vehicle.getIntersectPoint(start, end, intersect1);
      if (intersect2 != null) {
        mod += 0.125f;
      }
      end.y -= 0.3f;
    }
    BaseVehicle.releaseVector3f(start);
    BaseVehicle.releaseVector3f(end);
    BaseVehicle.releaseVector3f(intersect1);
    // logf("vehiclemod", mod);
    return mod;
  }

  // :todo consider adding bushes to set of things
  public static boolean checkFlags(IsoGridSquare c, boolean n, boolean w) {
    return (n && (c.getProperties().has(IsoFlagType.collideN) || c.getProperties().has(IsoFlagType.WindowN) || c.getProperties().has(IsoFlagType.doorN))) || (w && (c.getProperties().has(IsoFlagType.collideW) || c.getProperties().has(IsoFlagType.WindowW) || c.getProperties().has(IsoFlagType.doorW))) || c.getProperties().has(IsoFlagType.solidtrans) || c.getProperties().has(IsoFlagType.blocksight) || c.getProperties().has("CloseSneakBonus");
  }

  public static boolean isNearWallSneaking(final IsoPlayer player) {
    // :note original uses bSneaking directly, but that seems inappropriate
    // :todo check squares are not null
    final IsoGridSquare c = player.getCurrentSquare();

    final IsoGridSquare cn = c.getAdjacentSquare(IsoDirections.N);
    final IsoGridSquare cs = c.getAdjacentSquare(IsoDirections.S);
    final IsoGridSquare ce = c.getAdjacentSquare(IsoDirections.E);
    final IsoGridSquare cw = c.getAdjacentSquare(IsoDirections.W);

    final IsoGridSquare cne = c.getAdjacentSquare(IsoDirections.NE);
    final IsoGridSquare cnw = c.getAdjacentSquare(IsoDirections.NW);
    final IsoGridSquare cse = c.getAdjacentSquare(IsoDirections.SE);
    final IsoGridSquare csw = c.getAdjacentSquare(IsoDirections.SW);

    return player.isSneaking() && (
           checkFlags(c, true, true)
        || checkFlags(cs, true, true)
        || checkFlags(ce, true, true)
        || checkFlags(cse, true, true)

        || checkFlags(cn, false, true)
        || checkFlags(cne, false, true)

        || checkFlags(cw, true, false)
        || checkFlags(csw, true, false)

        || checkFlags(cnw, false, false)

        // || anyNearVehicles(player)
      )
      ;
  }

  private static float closestPointLine(final float x1, final float y1, final float x2, final float y2,
                                    final float x3, final float y3,
                                    final Vector2 output
      ) {

    projection_target.x = x3 - x2;
    projection_target.y = y3 - y2;
    final float projection_target_length = projection_target.getLength();
    projection_target.normalize();

    projection.x = x1 - x2;
    projection.y = y1 - y2;

    final float dot = projection.dot(projection_target);

    if (dot <= 0.0) {
      output.x = x2;
      output.y = y2;
    } else if (dot >= projection_target_length) {
      output.x = x3;
      output.y = y3;
    } else  {
      output.x = x2 + dot*projection_target.x;
      output.y = y2 + dot*projection_target.y;
    }

    final float dx =  output.x - x1;
    final float dy =  output.y - y1;
    return dx*dx + dy*dy;
  }

  public static float alignment(final IsoPlayer player, final float cx, final float cy) {
    return alignment(player, cx, cy, 0.0f);
  }

  public static float alignment(final IsoPlayer player, final float cx, final float cy, final float dotfudge) {
    // final Vector2 pzv = IsoZombie.tempo;
    float dot;

    pov.x = cx - player.getX();
    pov.y = cy - player.getY();
    pov.normalize();
    dot = pzv.dot(pov);
    dot += dotfudge;
    if (dot > 1.0f) {
      dot = 1.0f;
    }
    if (dot > 0.0f) {
      return dot;
    }
    return 0.0f;
  }

  // public static void loadNearishVehicles(final IsoPlayer player) {
  //   final float threshold2 = 36.0f;
  //   final ArrayList<BaseVehicle> vehicles = IsoZombiePatch.nearVehicles;
  //   vehicles.clear();
  //   if (player.getVehicle() != null) {
  //     return;
  //   }

  //   final int n = ((int)player.getX() - 4) / 10 - 1;
  //   final int n2 = ((int)player.getY() - 4) / 10 - 1;
  //   final int n3 = (int)Math.ceil((player.getX() + 4.0f) / 10.0f) + 1;
  //   for (int n4 = (int)Math.ceil((player.getY() + 4.0f) / 10.0f) + 1, i = n2; i < n4; ++i) {
  //     for (int j = n; j < n3; ++j) {
  //       final IsoChunk isoChunk = GameServer.bServer ? ServerMap.instance.getChunk(j, i) : IsoWorld.instance.CurrentCell.getChunk(j, i);
  //       if (isoChunk != null) {
  //         for (int k = 0; k < isoChunk.vehicles.size(); ++k) {
  //           final BaseVehicle baseVehicle = isoChunk.vehicles.get(k);
  //           if (baseVehicle.getScript() != null) {
  //             if ((int)player.getZ() == (int)baseVehicle.getZ()) {
  //               if (player.DistToSquared((float)(int)baseVehicle.x, (float)(int)baseVehicle.y) < threshold2) {
  //                 vehicles.add(baseVehicle);
  //               }
  //             }
  //           }
  //         }
  //       }
  //     }
  //   }
  // }

  // private static boolean isNearVehicle(final IsoPlayer player, final BaseVehicle vehicle) {
  //   final float maxNearVehicleDistance = 1.5f;
  //   closestPointVehicle(player.x, player.y, vehicle, intersection);
  //   intersection.x -= player.x;
  //   intersection.y -= player.y;
  //   return intersection.getLength() < maxNearVehicleDistance;
  // }

  // public static boolean anyNearVehicles(final IsoPlayer player) {
  //   loadNearishVehicles(player);
  //   for (BaseVehicle vehicle : IsoZombiePatch.nearVehicles) {
  //     if (isNearVehicle(player, vehicle)) {
  //       return true;
  //     }
  //   }
  //   return false;
  // }

  // private static float[] tempFloats;
  // static {
  //   tempFloats = new float[8];
  // }

  // public static boolean closestPointVehicle(final float x, final float y, final BaseVehicle baseVehicle, final Vector2 output) {
  //   if (baseVehicle == null || baseVehicle.getScript() == null) {
  //     log("vehicle null");
  //     return false;
  //   }
  //   tempFloats[0] = baseVehicle.getPoly().x1;
  //   tempFloats[1] = baseVehicle.getPoly().y1;
  //   tempFloats[2] = baseVehicle.getPoly().x2;
  //   tempFloats[3] = baseVehicle.getPoly().y2;
  //   tempFloats[4] = baseVehicle.getPoly().x3;
  //   tempFloats[5] = baseVehicle.getPoly().y3;
  //   tempFloats[6] = baseVehicle.getPoly().x4;
  //   tempFloats[7] = baseVehicle.getPoly().y4;
  //   float bestCandidateLength2 = Float.MAX_VALUE;
  //   for (int i = 0; i < 8; i += 2) {
  //     final float n6 = tempFloats[i % 8];
  //     final float n7 = tempFloats[(i + 1) % 8];
  //     final float n8 = tempFloats[(i + 2) % 8];
  //     final float n9 = tempFloats[(i + 3) % 8];

  //     final float dist2 = closestPointLine(x, y, n6, n7, n8, n9, candidate);
  //     if (dist2 < bestCandidateLength2) {
  //       bestCandidateLength2 = dist2;
  //       output.set(candidate);
  //     }
  //   }
  //   return true;
  // }

  // NOISE
  private static float objectNoiseLevel(IsoGameCharacter player) {
    float noise = 0.0f;
    final IsoGridSquare current = player.getCurrentSquare();
    if (current == null) {
      return noise;
    }
    final int r = 2;
    final IsoCell cell = current.getCell();

    for (int dx=-r; dx<=r; dx++) {
      for (int dy=-r; dy<=r; dy++) {
        final IsoGridSquare c = cell.getGridSquare(current.x+dx,current.y+dy,current.z);
        if (c == null) {
          continue;
        }
        float n = 0.0f;
        final PZArrayList<IsoObject> objects = c.getObjects();
        for (int i=0; i<objects.size(); i++) {
          final IsoObject e = objects.get(i);
          if (e instanceof IsoTree) {
            n += 2.0f;
          } else if (e.sprite.isBush) {
            n += 1.0f;
          // } else if (e.sprite.moveWithWind) {
          //   n += 1.0f;
          // } else if (e.sprite.getProperties().Is(IsoFlagType.vegitation)) {
          //   n += 1.0f;
          } else if (e.isFloor()) {
            continue;
          } else {
            n += .2f;
          }
        }

        for (IsoMovingObject e: c.getMovingObjects()) {
          if (e == player) {
            continue;
          }
          if (e instanceof IsoZombie) {
            n += 2.0f;
          } else if (e instanceof IHumanVisual) {
            n += 1.0f;
          }
        }

        // :todo weight different kinds of things differently;
        //       especially things that are human shaped.
        n += 0.5f * (float) c.getStaticMovingObjects().size();
        n += 0.1f * (float) c.getWorldObjects().size();

        final float weight = 1f / (1f + sqrt(dx*dx + dy*dy));
        noise += n*weight;
        // point3(c.x, c.y, c.z, 0.1f*n);
      }
    }

    // shape it
    return 8.f*(1.f - (4.f/(4.f+noise)));
  }

  // MISC
  private static final NoticePlayer getNoticeForPlayer(IsoZombie zombie, IsoPlayer player) {
    return getNotice(zombie).forPlayer.computeIfAbsent(player, z -> new NoticePlayer());
  }

  private static final Notice getNotice(IsoZombie zombie) {
    return notice_levels.computeIfAbsent(zombie, z -> new Notice());
  }

  // DEBUG
  public static void log_always(String fmt, Object... args) {
    DebugLog.log(DebugType.General, String.format(fmt, args));
  }

  public static void log(String fmt, Object... args) {
    if ((IngameState.instance.numberTicks % 60) == 0) {
      log_always(fmt, args);
    }
  }

  public static void logf(String tag, float x) {
    log("%-20s: %f", tag, x);
  }

  // DEBUG GRAPHICAL
  private static void circle_full(final float x1, final float y1, final float z1, final float size, final float r, final float g, final float b, final float a) {
      LineDrawer.DrawIsoCircle(x1,y1,z1, size, 32, r,g,b,a);
  }
  private static void line_full(final float x1, final float y1, final float z1, 
                                final float x2, final float y2, final float z2,
                                final float r, final float g, final float b, final float a) {
      LineDrawer.DrawIsoLine(x1,y1,z1, x2, y2, z2, r, g, b, a, 1);
  }
  public static void render_zombie_debug(IsoZombie zombie) {
    if (!DebugOptions.instance.character.debug.render.vision.getValue()) {
      return;
    }
    Notice notice = getNotice(zombie);
    for (var entry : notice.forPlayer.entrySet()) {
      IsoPlayer player = entry.getKey();
      NoticePlayer notice_player = entry.getValue();
      circle_full(
          zombie.getX(),
          zombie.getY(),
          zombie.getZ(),
          .4f*clamp01(sqrt(notice_player.v1)),
          clamp01(notice.v2/3f),
          clamp01((3.f-notice.v2)/3f),
          0.f,
          clamp01(sqrt(notice_player.v1))
          );
    }
    // its private
    // if (zombie.delayedSound.x != -1) {
    //   line_full(
    //       zombie.getX(),
    //       zombie.getY(),
    //       zombie.getZ(),
    //       zombie.delayedSound.x,
    //       zombie.delayedSound.y,
    //       zombie.delayedSound.z,
    //       0f,
    //       0f,
    //       1f,
    //       1f
    //       );
    // }
    // Object target = zombie.soundSourceTarget;
    // if (target != null && target instanceof IsoPlayer) {
    //   log_always("zombie sound target is player");
    //   IsoPlayer player = (IsoPlayer) target;
    //   circle_full(
    //       player.getX(),
    //       player.getY(),
    //       player.getZ(),
    //       .2f,
    //       0f,
    //       0f,
    //       1f,
    //       1f
    //       );
    //   line_full(
    //       zombie.getX(),
    //       zombie.getY(),
    //       zombie.getZ(),
    //       player.getX(),
    //       player.getY(),
    //       player.getZ(),
    //       0f,
    //       0f,
    //       1f,
    //       1f
    //       );
    // }
    zombie.characters.IsoGameCharacter.Location lastHeard = zombie.getLastHeardSound();
    PathFindBehavior2 pfb = zombie.getPathFindBehavior2();
    if ((lastHeard != null && lastHeard.x != -1)) {
      circle_full(
          lastHeard.x,
          lastHeard.y,
          lastHeard.z,
          .2f,
          0f,
          1f,
          0f,
          1f
          );
      line_full(
          zombie.getX(),
          zombie.getY(),
          zombie.getZ(),
          lastHeard.x,
          lastHeard.y,
          lastHeard.z,
          0f,
          1f,
          0f,
          1f
          );
    } else if (pfb.isGoalSound()) {
      circle_full(
          pfb.getTargetX(),
          pfb.getTargetY(),
          pfb.getTargetZ(),
          .2f,
          0f,
          1f,
          0f,
          1f
          );
      line_full(
          zombie.getX(),
          zombie.getY(),
          zombie.getZ(),
          pfb.getTargetX(),
          pfb.getTargetY(),
          pfb.getTargetZ(),
          0f,
          1f,
          0f,
          1f
          );
    }
  }

  // limited to one player
  public static float player_debug_visibility = 0f;
  public static float player_debug_cover = 0f;
  public static void render_player_debug(IsoPlayer player) {
    if (!DebugOptions.instance.character.debug.render.vision.getValue()) {
      return;
    }
    // player only factors
    final float visibility = playerVisibility(player);
    final float weatherNoise = weatherNoiseLevel(player);
    final float objectNoise = objectNoiseLevel(player);
    final float noise = weatherNoise + objectNoise;
    final float playerSneakX = playerSneakLevelFactor(player);
    final float cover = pow(1.f/(1.f + noise), 1.f+playerSneakX); // the stretch here might be too strong

    // smooth it over a similar timescale
    final float k = kc(.9f, .1f);
    player_debug_visibility += visibility*(1f-k);
    player_debug_visibility *= k;
    player_debug_cover += cover*(1f-k);
    player_debug_cover *= k;

    // the idea is V measures visiblity directly ish, 
    // C represents adequacy of cover
    final float V = clamp(player_debug_visibility, 0f, 8f);
    circle_full(
        player.getX(),
        player.getY(),
        player.getZ(),
        .2f*sqrt(V),
        1f,
        1f,
        0f,
        .8f*clamp01(sqrt(V/8))
        );

    //
    final float C = clamp(1/(.01f+player_debug_visibility*player_debug_cover), 0, 8);
    circle_full(
        player.getX(),
        player.getY(),
        player.getZ(),
        .2f*sqrt(C),
        0f,
        0f,
        1f,
        .8f*clamp01(sqrt(C/8))
        );

    float px = player.getX();
    float py = player.getY();
    float pz = player.getZ();
    DCover dcover = directionalCoverFor(player);
    float dd = .7f;
    float de = .24f;
    //
    if (dcover.N > 0f) {
      line_full(px-de, py-dd, pz, px+de, py-dd, pz, 1f, 0f, 1f, 1f);
    }
    if (dcover.S > 0f) {
      line_full(px-de, py+dd, pz, px+de, py+dd, pz, 1f, 0f, 1f, 1f);
    }
    if (dcover.W > 0f) {
      line_full(px-dd, py-de, pz, px-dd, py+de, pz, 1f, 0f, 1f, 1f);
    }
    if (dcover.E > 0f) {
      line_full(px+dd, py-de, pz, px+dd, py+de, pz, 1f, 0f, 1f, 1f);
    }
    //
    if (dcover.NW > 0f) {
      line_full(px-dd, py-de, pz, px-de, py-dd, pz, 1f, 0f, 1f, 1f);
    }
    if (dcover.NE > 0f) {
      line_full(px+dd, py-de, pz, px+de, py-dd, pz, 1f, 0f, 1f, 1f);
    }
    if (dcover.SW > 0f) {
      line_full(px-dd, py+de, pz, px-de, py+dd, pz, 1f, 0f, 1f, 1f);
    }
    if (dcover.SE > 0f) {
      line_full(px+dd, py+de, pz, px+de, py+dd, pz, 1f, 0f, 1f, 1f);
    }
  }

  // MATH
  private static float kc(float l, float t) {
    final float T = GameTime.instance.getTimeDelta();
    return (float)Math.pow(l, T/t);
  }
  private static float ind(boolean b) { return b? 1.0f:0.0f; }
  private static float exp(float x) { return (float) Math.exp(x); }
  private static float sqrt(float x) { return (float) Math.sqrt(x); }
  private static float pow(float x, float e) { return (float) Math.pow(x, e); }
  private static float log10(float x) { return (float) Math.log10(x); }
  private static float log2(float x) { return ((float) Math.log(x)) / ((float)Math.log(2)); }
  private static float sin(float x) { return (float) Math.sin(x); }
  private static float cos(float x) { return (float) Math.cos(x); }
  private static float abs(float x) { return (float) Math.abs(x); }
  private static float sin_shape(float x) { return (float) (.5*(1.0+Math.sin(((double)x * Math.PI)-Math.PI/2))); }
  public static float max(float v0, float... vs) {
    float m = v0;
    for (float v : vs) {
      if (v > m) {
        m = v;
      }
    }
    return m;
  }
  public static float min(float v0, float... vs) {
    float m = v0;
    for (float v : vs) {
      if (v < m) {
        m = v;
      }
    }
    return m;
  }
  private static float clamp(final float x, final float min, final float max) {
    if (x < min) {
      return min;
    }
    if (x > max) {
      return max;
    }
    return x;
  }

  private static float clamp01(final float x) {
    if (x < 0.0f) {
      return 0.0f;
    }
    if (x > 1.0f) {
      return 1.0f;
    }
    return x;
  }
}
