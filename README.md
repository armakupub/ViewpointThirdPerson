# Third Person Camera for Project Viewpoint

A proper third-person camera for [Project Viewpoint](https://steamcommunity.com/sharedfiles/filedetails/?id=3809306528), on foot and in vehicles, for Project Zomboid B42. It plays like most third-person action games: look around freely, your character runs wherever you go. Made for **mouse and keyboard**, with full **controller** support.

**On foot**

- **Free camera**: look around without turning your character. Your character only turns with the camera while you aim (right mouse button).
- **No more backpedalling**: press S and your character turns around and runs towards the camera, and keeps facing that way when you stop. Want the old walk back? Turn on "Backpedal when walking backwards".
- While you walk, the camera swings back behind you once you leave the mouse alone for 2 seconds. "Swing delay" changes the time, 0 turns it off.
- Wide view in melee to watch your flanks, over the shoulder when you aim a gun.
- Pulls in when you go indoors and back out when you leave, and eases in before walls instead of snapping.
- Zoom with the mouse wheel.
- Menus and windows free the cursor and lock it again when you close them. With the cursor free, hold the right mouse button to aim and turn the camera, let go and the cursor is free again. A short right click still opens the context menu.
- Your accuracy is untouched, it is still down to your Aiming skill. This mod does not change it and never will.

**In vehicles**

- The camera sits behind you at a distance that fits each vehicle, KI5 vehicles included, and over the roof of tall trucks and vans.
- Look around freely, after 2 seconds it swings back in behind you.
- Leans into how you drive ("Speed feel") and stays smooth in tight turns and at high frame rates, trailers included.

**Settings and keys:** everything is in Viewpoint's own settings window (Delete key), on the **Third person** tab. The keys for swapping shoulders, freeing the cursor and looking around are under **Third person, Keys**, not bound by default.

**Controller:** within the game's own layout the right stick turns the camera on foot and in vehicles, and aiming moves to RT, or to LT with the bundled preset. Viewpoint's views switch from the game's Back wheel. The character runs wherever the left stick points, the camera swings in behind once the stick rests, and D-pad Down and RB work Viewpoint's loot panel while it shows. The preset **Viewpoint Third Person** appears in Options, Controller, Preset after a save with the mod has been loaded once.

## Screenshots

![Controller layout](screenshots/controller_layout.png)

![Third person settings](screenshots/settings_third_person.png)

![Viewpoint's views on the controller's Back wheel](screenshots/controller_back_wheel.png)

## Requirements

- Project Zomboid B42.21
- [ZombieBuddy](https://steamcommunity.com/sharedfiles/filedetails/?id=3619862853)
- Project Viewpoint

## Building

Copy `build.local.example` to `build.local`, set the paths, then run `bash build.sh`. It compiles against Project Zomboid, ZombieBuddy and Viewpoint and installs the mod into `~/Zomboid/mods/ViewpointThirdPerson`.

## License

MIT
