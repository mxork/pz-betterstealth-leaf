# Better Stealth

Overhaul of stealth mechanics for Project Zomboid.

Requires [Leaf](https://pzwiki.net/wiki/Leaf) for Java bytecode modding.

### Installation

See <https://github.com/LeafPZ/leaf-installer> to install Leaf.

Mod is on [Steam](https://steamcommunity.com/sharedfiles/filedetails/?id=3815547024).

### Development setup

See <https://github.com/LeafPZ/leaf-example-mod> for build instructions.

`pz.mxork.Patch.spotted` is probably where you want to start reading if you're feeling curious.

## The Problem

Project Zomboid's visual stealth system is broken. Here are some references:

- <https://www.youtube.com/watch?v=AdxrTJ7lZ0c>
- <https://theindiestone.com/forums/topic/101877-42204-stealth-is-bugged-not-working-as-intended/>
- <https://www.reddit.com/r/projectzomboid/comments/1kwzqp1/sneaking_is_still_broken_in_b42_and_it_shouldnt/>

TL;DR

1.  Zombies can't see you at all beyond a short distance.
2.  Within that distance, zombies roll to spot the player every frame.
3.  Because the roll happens so often, stealth is all or nothing. If a
    zombie *can* spot you, they *will* spot you very quickly.
4.  Even if the rolling was reduced, most modifiers to the likelihood of being
    spotted are so poorly balanced or implemented that their
    contributions are overwhelming, negligible, or inconsistent. This includes darkness, cover
    from fences, cover from cars, weather, player movement, and player sneak level.
6.  Once a zombie has spotted you, they use wall hacks to track your location,
    ignoring the stealth system entirely. The only way to break their ability to track
    you is to stand behind an opaque object until the wall hacks timeout.
7.  None of this can be fixed by a Lua mod. Stealth lives entirely in Java.

## Visual stealth

Better Stealth ignores the vanilla system and reimplements most of it from scratch.

- Zombies accumulate "notice" over a window of about 300ms. If notice goes above a threshold, they spot you.
  Zombies can still spot you instantly, but a glimpse of you moving through cover won't give you away.

- Zombies don't use wall hacks when hunting. They receive a bonus to their spotting ability when irritated or hunting,
  which slowly decays if they lose track of their target. You *can* lose a zombie by ducking behind a fence and slinking away.

- Zombies also get a bonus to hearing when irritated. They might not see you, but they can follow your footsteps. See **Auditory stealth** for more.

### Variables

These are the variables that affect being spotted:

- Player light level, both absolute and relative to the spotting zombie. Careful with torches at night.
- Player movement, including performing actions and attacking
- Player traits, conspicuous and inconspicuous
- Non-directional cover from trees, bushes, shrubs, and clutter
- Directional cover from fences and walls, which stays active going around corners
- Directional cover from cars, which uses multiple test points so you can be partially obscured
- Directional cover from obstacles that look like they should provide cover, but vanilla forgot about
- Weather, including fog, rain, snow, and wind
- Zombie to player distance; there is no hard cap on how far away a zombie can spot you.
  Don't be surprised if wandering in the open pulls zombs in from offscreen.
- Zombie to player view angle; they have some peripheral vision, but if a zombie turns in your direction, freeze.
- Zombie irritation, caused by sounds or losing track of a target
- Player stealth skill; it makes you better at using cover and darkness to remain unseen,
  but stand in broad daylight and zombies will see you just fine.

All parameters have been modelled and play-tested to ensure they match a player's intution.

## Auditory stealth

Auditory stealth isn't nearly as broken in vanilla as visual stealth, but it needed a few tweaks to complement the visual changes.

- In vanilla, sounds have a fixed volume and radius within which they can be heard.
- In Better Stealth, there is no hard cap on how far away a zombie can hear a sound.
- Internally, sound volumes are converted to decibels so they can be modelled against real world equivalents.
  A footstep sneaking is ~30dB at 3 squares distance. A car alarm is ~90dB at 100 squares.
- Background noise levels are calculated for each zombie. A sound 12dB quieter than their surroundings won't be noticed.

### Variables

These variables affect the volume of player footsteps:

- Sneaking, running or sprinting. Aiming uses the sneak volume, to avoid penalizing a player for looking around.
- Bodyweight
- Inventory weight
- Shoe weight and stomp power. Grass reduces the penalty from shoes.
- Player skills; lightfoot, sneak and nimble all contribute, but lightfoot is much more significant.
- (todo) More terrain effects
- (todo) Objects on ground

These variables affect the level of background noise, which helps mask player sounds:

- Weather
- Being outside
- World sounds from alarms, gunshots, thumping, animals.
- Other zombies standing nearby
- Electricity; the world gets quieter when the power shuts off.
- Distance
- Walls

## What's happening under the hood

Zomboid makes it hard to tell what is happening with stealth, which is probably why you can
almost pretend it works.

I've added a bunch of debug visuals under the `character.debug.render.vision` flag:

- Notice levels are shown as an expanding ring around the zombie which turns red when irritated.
- Player visibility and non-directional cover levels are shown as two rings around the player. Visibilty is yellow, cover is blue.
  If visibility is greater than cover, you'll probably get spotted.
- Player directional cover from fences and walls appear as a purple segment in each of the eight cardinalish directions if active.
- Directional cover from cars has no visuals. Its tricky, and is computed per-zombie.

## Caveats

- Single player only. Adding multiplayer is possible, but the relevant code is
  sprinkled over hundreds of if statements and distributed state.

- Proof-of-concept. I made this for myself, because I wanted a Zomboid
  stealth-horror experience, and vanilla just doesn't work. No promises to
  improve or support this mod (especially when updates butcher my bytecode
  patches), but anyone is welcome to fork it or use it as a reference.

- Animations, sounds, AI. Stealth works best when the player can tell what is happening,
  whether a zombie is aggro'd or just suspicious, how loud their footsteps are,
  how dark is too dark to see. I'd love if PZ had more I could recycle into giving
  players immersive feedback, but it doesn't, and adding it would be a bunch of work
  that would probably break on the next update.

- Performance. I've done nothing in particular to make the code run fast. It runs well enough on my machine,
  but if your machine struggles with large hordes of zombies, this mod will make it worse. Let me know
  if its a problem. There's probably about 10-20x of easyish optimizations I could implement.

- Sandbox settings. I've done nothing to respect the sandbox settings for
  zombie hearing or vision. They're ignored. I'd like to expose a bunch of
  different parameters which are more interesting and better balanced, as well
  as expose stealth internals to Lua modders. But first, I want to make sure
  the fundamentals are Good Enough.

- Code quality. The code is great. Everything lives in one big class full of
  static methods. It has dead code, comments that no longer apply, and
  inconsistent case conventions. Bluntly, I don't think it matters. Its robust
  where it needs to be, and you can see what I was thinking while implementing
  it. If you care enough to read the code and are having a hard time understanding it,
  ping me in the PZ Modding Community discord and I'll help you out.
