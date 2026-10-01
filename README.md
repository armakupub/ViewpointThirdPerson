# Third Person Camera for Project Viewpoint

An add-on for [Project Viewpoint](https://steamcommunity.com/sharedfiles/filedetails/?id=3809306528) that turns its third-person view into a full camera rig for Project Zomboid B42.

- **On foot:** over the shoulder, with smoothed follow, shoulder swap, mouse wheel distance and a closer view indoors. The combat stance backs off to keep both flanks in view; aiming a firearm moves in over the shoulder.
- **In vehicles:** a chase camera with weight. It falls behind when you speed up, closes in when you brake, drifts wide in a turn and swings in behind where you are going. Distance and height follow the vehicle's size, trailers included.
- **Cursor key:** a keyboard key for Viewpoint's cursor toggle, in place of the middle mouse button.

All settings are in Viewpoint's settings window under **Third person**, the keys under **Keys, Third person camera**.

## Requirements

- Project Zomboid B42.21
- [ZombieBuddy](https://steamcommunity.com/sharedfiles/filedetails/?id=3619862853)
- Project Viewpoint

## Building

Copy `build.local.example` to `build.local`, set the paths, then run `bash build.sh`. It compiles against Project Zomboid, ZombieBuddy and Viewpoint and installs the mod into `~/Zomboid/mods/ViewpointThirdPerson`.

## License

MIT
