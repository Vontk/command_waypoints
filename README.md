<div align="center">
<img src="https://cdn.modrinth.com/data/cached_images/efac58e3820179642c8a548e0d264650c955b0de.png">
<br><br><br>

In 25w15a, Mojang released the Locator Bar, which can show waypoints. Currently, only entities can be waypoints with players showing by default.

This mod extends the `/waypoint` command to allow adding/modifying/removing of "static" waypoints.

<br>
<img src="https://cdn.modrinth.com/data/cached_images/efac58e3820179642c8a548e0d264650c955b0de.png">
</div>
<br><br>

### Commands

- `/waypoint add <id>` creates a visible waypoint named `<id>` at your current X/Z position, in your current dimension.
- `/waypoint goto <id> <dimension> <x> <z>` creates or moves a named destination. The dimension is required: `overworld`, `nether`, or `end`. Coordinates are whole numbers in the specified dimension. This sets a destination; it does not teleport you. Moving an existing waypoint preserves its appearance and visibility.
- `/waypoint <id> visible <true|false>` shows or hides the waypoint without deleting it.
- `/waypoint <id> color <color>` changes its named color.
- `/waypoint <id> color hex <color>` changes its hexadecimal color.
- `/waypoint <id> style reset` resets its icon style.
- `/waypoint <id> style set <style>` changes its icon style.
- `/waypoint <id> range <range>` changes its horizontal display range, measured in blocks in the viewer's current dimension.
- `/waypoint remove <id>` removes a waypoint.
- `/waypoint list` lists names, original coordinates/dimensions, and visibility for the current dimension set.

The custom commands no longer require `static` or `modify`. Custom waypoint commands work in singleplayer survival without enabling cheats. Dedicated servers retain the operator requirement because waypoints are shared server data. Vanilla entity waypoint commands retain their vanilla syntax and permission requirements. Names use Minecraft identifiers (an omitted namespace defaults to `minecraft:`); the locator bar retains vanilla icon rendering.

### Dimensions and visibility

Overworld and Nether waypoints share one set of names. A Nether destination at `10 -20` appears in the Overworld at `80 -160`. An Overworld destination at `80 -160` appears in the Nether at `10 -20`. Conversion retains fractional coordinates internally, including negative values.

The End has a separate set, visible only in the End. End waypoints never appear in the Overworld or Nether, and Overworld/Nether waypoints never appear in the End. The same name can exist independently in the End and in the Overworld/Nether set. Other dimensions remain isolated.

Height is ignored for direction and range. Waypoints use the vanilla locator bar's horizontal direction packets, so they do not display elevation arrows. Visibility and appearance changes are shared by everyone receiving that waypoint, following the original server-side mod's behavior. Hidden waypoints remain saved and listed. Previously saved waypoints load as visible and retain their original owning dimension.

Example:

```text
/waypoint add home
/waypoint home color green
/waypoint home visible false
/waypoint home visible true
/waypoint goto fortress nether -100 250
/waypoint goto end_city end 1200 -800
```

### Building for Minecraft 26.3 (Fabric)

Requires Java 25. Run `./gradlew :fabric:build` (includes coordinate conversion and login command-packet checks); the installable jar is in
`fabric/build/libs/` (use the jar without the `-sources` suffix). Runtime
requirements are Fabric Loader 0.19.5 or newer and Fabric API 0.161.0+26.3
or newer for Minecraft 26.3. The Fabric build includes the existing common
sources directly, using Fabric Loom for Minecraft's unobfuscated releases.
