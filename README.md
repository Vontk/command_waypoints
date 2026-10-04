# Command Waypoints +

Add fixed destinations to Minecraft’s **Locator Bar**. This enhanced edition puts waypoint management in a familiar Minecraft GUI.

![Minecraft locator bar — original Command Waypoints artwork](https://cdn.modrinth.com/data/cached_images/efac58e3820179642c8a548e0d264650c955b0de.png)

## Your destinations, one menu

Press **N** in-game to open the manager, or **B** to add a waypoint at your current location. Both keys are configurable. With optional **Mod Menu**, you can also open **Mods → Command Waypoints + → Config**.

![Waypoint manager](docs/images/manager.png)

- **Create & edit** — name, X/Z, dimension, a visual color picker, marker-shape previews, Infinite or Limited range, and visibility.
- **Find & organize** — search, filter by dimension, hide, or delete.
- **Share & save** — send in public chat or choose an online player; click **[Add]** to keep a personal copy.
- **Follow naturally** — icons grow as you approach; size and growth distance are configurable.

![Waypoint editor](docs/images/editor.png)

Overworld ↔ Nether coordinates convert **8:1**. End destinations stay in the End.

## Install

**Minecraft 26.3 · Fabric**

Put the mod and **Fabric API** in your instance’s `mods` folder. **Mod Menu is optional** — N and B work without it. Install on client and server for multiplayer. Replace the original Command Waypoints jar.

[Downloads](https://modrinth.com/mod/command-waypoints-plus) · [Full guide](docs/GUIDE.md) · [GitHub releases](https://github.com/Vontk/command_waypoints/releases)

### Prefer commands?

Commands still work: `/waypoint add Home`, `/waypoint "Home Base" visible false`, `/waypoint list all`, and `/waypoint share Home Alex`. Names support capitals and quoted spaces; no `minecraft:` prefix is needed. [Command reference](docs/GUIDE.md#commands).

---

An independent fork of [Command Waypoints](https://modrinth.com/mod/command-waypoints) by **Jakob (Minenash)**, maintained by **Vontk** under [LGPL-3.0-or-later](LICENSE.txt). Original locator-bar artwork credited above; GUI images are Minecraft screenshots. Fork enhancements and documentation were developed with Codex assistance.
