# Slime pet models

28 slime pets for Roblox, from Common to Divine. Each slime is a `.glb` in `models/`. Every model:

- is modelled in **studs** (1 unit = 1 stud)
- faces forward: the face looks down **−Z**, Roblox's LookVector
- has its **bottom centre at the origin**, so it sits on the ground
- stays **under 3,000 triangles**

| # | Model | Rarity | Size (studs) | Triangles |
|---|---|---|---|---|
| 1 | LilGreen | Common | 3 | 1,640 |
| 2 | BlueBlob | Common | 3 | 2,264 |
| 3 | Pinky | Common | 3 | 2,224 |
| 4 | Mudsy | Common | 3 | 2,700 |
| 5 | Bubblegum | Uncommon | 3.2 | 2,480 |
| 6 | LemonDrop | Uncommon | 3.2 | 2,540 |
| 7 | Minty | Uncommon | 3.2 | 2,052 |
| 8 | TopHat | Rare | 3.6 | 2,816 |
| 9 | Ninja | Rare | 3.6 | 2,708 |
| 10 | Lava | Rare | 3.6 | 2,168 |
| 11 | Frost | Epic | 4 | 2,568 |
| 12 | Royal | Epic | 4 | 2,904 |
| 13 | Ghost | Epic | 4 | 2,532 |
| 14 | Devil | Legendary | 4.5 | 2,644 |
| 15 | Angel | Legendary | 4.5 | 2,768 |
| 16 | Galaxy | Legendary | 4.5 | 2,680 |
| 17 | Dragon | Mythic | 5.2 | 2,736 |
| 18 | Void | Mythic | 5.2 | 2,296 |
| 19 | King | Secret | 6.5 | 2,928 |
| 20 | Glitch | Secret | 6.5 | 916 |
| 21 | Gummy | Legendary | 4.5 | 2,968 |
| 22 | CottonCandy | Mythic | 5.2 | 2,924 |
| 23 | Thunder | Secret | 6.5 | 2,532 |
| 24 | Phoenix | Secret | 6.5 | 2,562 |
| 25 | Supernova | Divine | 7 | 2,840 |
| 26 | BlackHole | Divine | 7 | 2,820 |
| 27 | Omega | Divine | 7 | 2,936 |
| 28 | Overlord | Secret (Boss Shop) | 6.5 | 2,828 |

"Size" is the width of the slime's body. Hats, wings, tails and other extras stick out past it.

## Zone guardians

The monsters that chase you, in `guardians/`. They follow the same rules as the slimes (studs, facing −Z,
bottom centre at the origin) and stay under 5,000 triangles. Each one has a `Body` part and an angry but
kid-friendly face.

| Model | Body width (studs) | Triangles | Look |
|---|---|---|---|
| Guardian_Meadow | 9 | 4,578 | Dark green, thorny vines, a flower on its head, grumpy brows |
| Guardian_Swamp | 10 | 4,920 | Murky olive-teal, dripping goo, lily pad on top, mushrooms on its back |
| Guardian_Lava | 11 | 3,388 | Cracked black rock over a glowing orange core, horns, jagged spikes |
| Guardian_Crystal | 12 | 3,184 | Icy purple-blue, large crystal spikes along its back, glowing eyes |
| Guardian_Void | 14 | 3,368 | Pure black, seven glowing purple eyes, floating shards and orbs |
| Guardian_Candy | 15 | 4,712 | Sugar Rush: pink candy monster, dripping icing, sprinkles, candy-cane horns, gumdrops |
| Guardian_Storm | 16 | 4,216 | Thunder Peaks: storm cloud top, lightning-bolt horns, glowing yellow eyes, sparks |
| Guardian_Cosmic | 18 | 4,728 | Star Core: starry deep-space body, planet ring, orbiting moons, glowing eyes |
| Guardian_Boss | 20 | 4,332 | The Slime Overlord boss: spiked crown, cape, gold shoulder spikes, glowing red eyes, power orbs |

Import them the same way as the slimes, then run [`roblox/GuardianSetup.lua`](roblox/GuardianSetup.lua)
instead of `SlimeSetup.lua`. `Body` becomes the PrimaryPart, so you can move a guardian with
`model:PivotTo(...)` or weld `Body` to whatever drives it.

## Player base

[`base/BaseBuilder.lua`](base/BaseBuilder.lua) builds a 52 × 40 stud base out of normal Roblox Parts:
paste it into the Command Bar and press Enter. It makes a model called `SlimeBase` at `PLACE_AT` (set at
the top of the script). Its pivot is the bottom centre and the entrance faces −Z, so you can place or clone
it with `base:PivotTo(cframe)`.

What the base has, by the names your scripts can use:

- `Slots/Slot1` to `Slot10`: five pads on the floor and five on a raised back tier, so back-row slimes are
  never hidden. Each pad has a `Top.PetSpot` attachment where the pet stands, facing the street, a neon
  `Rim` to recolour by rarity, and a `Plaque.PlaqueGui.Label` for the earnings text.
- `Entrance/Sign.SignGui`: a round `Avatar` image and the `OwnerName` text. In Studio it fills in your
  own name and headshot. In game, set
  `Avatar.Image = ("rbxthumb://type=AvatarHeadShot&id=%d&w=150&h=150"):format(player.UserId)`.
- `Door/Field` and `Door/Beam`: the laser door, with a `LockTimer` billboard. The end of the script has
  a `setDoorLocked` snippet.
- `LockButton/Button`: the pad you step on to lock the base.

Colours are set at the top of `tools/build_base.py`. Change them and run
`python3 slimes/tools/build_base.py` to get a new builder script.

## Map props and valley dressing

In `props/`, same conventions (studs, front faces −Z, pivot at the bottom centre).

| Model | What it is | Height (studs) |
|---|---|---|
| ValleyGate | Slime-tower arch for the valley entrance, 100 wide, "SLIME VALLEY" sign | 30.4 |
| ZoneArch_Meadow / Swamp / Lava / Crystal / Void | Themed arch to replace each zone banner, 96 wide, sign with zone name and rarity line | 31–37 |
| Cliff_Meadow / Swamp / Lava / Crystal / Void | Themed cliff piece (24 wide) that CliffDresser stretches onto your wall blocks | 35–40 |
| Meadow_FlowerClump, Meadow_Mushrooms, Meadow_Sunflower | Meadow props | 4–11 |
| Swamp_DeadTree, Swamp_Reeds | Swamp props | 5–12 |
| Lava_Spire, Lava_Vent | Lava props | 6–13 |
| Crystal_Cluster, Crystal_Shards | Crystal props | 4–9 |
| Void_Obelisk, Void_Shards | Void props | 7–12 |
| Tree_A, Tree_B | Street trees | 21, 24 |
| PlazaStatue | King Slime statue on a plinth | 12 |

Steps in Studio (all scripts go in the Command Bar):

1. Import the `.glb` files, then run [`roblox/PropSetup.lua`](roblox/PropSetup.lua). It colours and anchors every
   part, turns collisions on only for solid parts (trunks, rocks, pillars), so players don't snag on petals and
   reeds, writes the sign text, adds lights to glowing parts and scales each prop to its height. Move the set-up
   props into ServerStorage; they are the templates.
2. Place `ValleyGate`, the five `ZoneArch` models (over your old banners), the trees and `PlazaStatue`. The arch
   signs show the zone name and rarity line; edit `SignGui*.SubLabel` to add the guardian speed text.
3. For each zone, select its floor, set `ZONE` at the top of [`roblox/ZoneDresser.lua`](roblox/ZoneDresser.lua) and
   run it. It scatters that zone's props, lays a themed path down the middle (stepping stones, a boardwalk, basalt
   over glowing lava, ice tiles, glowing void tiles) with the area around it kept clear for running, adds drifting
   particles and tags the floor with its zone.
4. Then select that zone's wall blocks and run [`roblox/CliffDresser.lua`](roblox/CliffDresser.lua) with the same
   `ZONE`. Each block gets cliff pieces stretched to its size, rocky face toward the valley; the block stays as an
   invisible collision wall, so gameplay doesn't change.
5. Run [`roblox/MapLighting.lua`](roblox/MapLighting.lua) once. It sets soft afternoon lighting with a light haze,
   bloom on neon and sun rays, and installs a `ZoneMood` LocalScript that fades the haze and colour as players walk
   between zones: warm in the Meadow, misty green in the Swamp, orange haze in the Lava zone, cool in the Crystal
   zone and dark purple in the Void.

Everything placed by the dressers goes into `Workspace > MapProps > <Zone>`. Running a dresser again replaces what
it placed before.

## Hub

Also in `props/`, set up by the same `PropSetup.lua`. Each sign panel gets its text from PropSetup; the panels
your game writes on are named so your scripts can find them.

| Model | What it is | Your scripts use |
|---|---|---|
| Hub_Leaderboard | Gold-framed board with a crown and coin piles, header "RICHEST SLIME LORDS" | Put your leaderboard SurfaceGui on `Screen` |
| Hub_HowToPlay | Cyan board with a slime mascot; the 9 steps are already on it | `Screen.SignGuiFront.BodyLabel` holds the text |
| Hub_BossShop | Market stall with a striped awning, token stacks and a treasure chest | Put your ProximityPrompt on `Counter` |
| Hub_FuseMachine | Three hoppers feeding a glowing orb, output pad at the front | `Base` for the prompt, `OutputPad` for the result |
| Hub_OverlordArena | 53-stud arena with torch pillars and a gate | `TimerScreen.SignGuiFront.Label` for the "OVERLORD ARRIVES IN" countdown |
| Hub_OverlordPedestal | Display stand for the Overlord with a glowing ring and nameplate | Stand the Overlord on top (2.9 studs up) |

## Importing into Roblox Studio

1. **File › Import 3D**, pick the `.glb` files. If the importer asks for a scale unit, choose **Stud**.
   Keep the model names (`LilGreen`, `BlueBlob`, ...).
2. Select the imported slimes. You can also select nothing, and the script will search Workspace,
   ReplicatedStorage and ServerStorage instead.
3. Paste all of [`roblox/SlimeSetup.lua`](roblox/SlimeSetup.lua) into the **Command Bar** and press Enter.

The script:

- sets each part's colour and material (Neon glows, Glass crystals, a see-through Ghost and Galaxy, Metal crowns)
- welds every part to `Body`, which becomes the PrimaryPart
- puts the pivot at the bottom centre, facing the way the face looks
- scales the model to its exact size
- adds `Rarity` and `Size` attributes

You can run it again safely. It only changes a model whose parts are all slime parts, so other models that happen to be named `King` or `Ghost` are left alone.

Each model is split into named parts (`Body`, `Eyes`, `Crown`, `Wings`, ...), each with one colour, so you can recolour a part or swap its material in Studio.

## Rebuilding

The models are generated by code, so you can tweak a slime and build again:

```sh
pip install numpy
python3 slimes/tools/build_slimes.py
python3 slimes/tools/build_guardians.py
python3 slimes/tools/build_base.py
python3 slimes/tools/build_props.py
```

`tools/build_slimes.py` holds one function per slime. It writes the `.glb` files, `models/slimes.json` and
`roblox/SlimeSetup.lua`, and stops with an error if any slime reaches 3,000 triangles.
`tools/build_guardians.py` does the same for the guardians, with a 5,000 triangle limit.
