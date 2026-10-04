# Integration checks

## Build checks

With Java 25, run `./gradlew :fabric:build`. This executes:

- Overworld/Nether 8:1 conversion, including negatives and fractional coordinates.
- End and custom-dimension isolation.
- Horizontal range math and cardinal directions.
- Bearings compared with Minecraft's actual `Vec3.rotateClockwise90()` math.
- Colored waypoint names, coordinate order, hidden markers and default colors.
- Minecraft's real serverless command-permission inspector and command-packet encode/decode, reproducing the previous login failure.

## Real Minecraft harness

Run `./gradlew :fabric:integrationTestJar` to build the separate test-only jar. Use two **disposable directories**, one for a localhost Fabric dedicated server and one for a client, each with the production mod, Fabric API, and the integration-checks jar. The client also uses Mod Menu for config integration checks. Do not put the test jar in a regular Prism instance: it deliberately creates/edits/deletes fixture points and drives screens automatically.

The server must run on `127.0.0.1:25577`, have `online-mode=false`, `enforce-secure-profile=false`, `white-list=false`, and `enforce-whitelist=false`. This offline configuration is only for a local test. A flat disposable world and small view distance keep test startup fast. Use the username `WaypointTester` for the client and a 1280×800 window, GUI scale 2. A separate virtual display can be used on Linux. Supply the normal Minecraft assets and runtime libraries through Fabric Loom's launch setup.

The server checks run after the client finishes login and verify:

- Non-operator personal navigation and retained vanilla entity-edit permissions.
- Empty-name generation and duplicate avoidance.
- Personal list and locator privacy.
- Personal data codec round-trip and legacy defaults.
- Private-share authorization against a different UUID.
- Independent copies preserving appearance/location, and one-time share acceptance.
- Editing, stable UUIDs, source-dimension list filtering, and all-dimension listing.

The client drives the actual native buttons and fields, sending production networking payloads:

- Create at the player's location; enter a readable name/color/coordinates; save.
- Create Nether and End destinations and list them with colored rows.
- Open public/private sharing; filter and select an online recipient.
- Receive formatted chat and invoke the exact command attached to **[Add]**.
- Verify the imported personal copy and collision suffix.
- Open settings and the native key-binding screen; verify linear icon-size endpoints/midpoint.
- Reject invalid editor coordinates; edit the existing UUID successfully.
- Toggle visibility, confirm deletion, open quick add via a registered key binding, and save a blank name.
- Send publicly and capture the chat interface.

Success markers are `WAYPOINT_SERVER_CHECKS_PASSED` and `WAYPOINT_CLIENT_CHECKS_PASSED`. Any `*_FAILED` marker means failure even if the Minecraft process remains running. Native screenshots are saved under the disposable client's `screenshots/`, named `manager`, `editor`, `settings`, `share-public`, `share-private`, `keybinds`, and `sharing-chat`. The test jar is never included in the production release.

## Manual acceptance checks

- Open **Mods → Command Waypoints → Config**, then Settings; check title-screen behavior and return navigation.
- Rebind both hotkeys using native Controls. Test real key presses, keyboard Tab navigation, tooltips, and Escape/Cancel without saving.
- Inspect the screens at GUI scales/window sizes that give at least 320×240 GUI pixels.
- Move towards a point from beyond the growth distance to zero; check continuous icon scaling. Look sharply up/down and verify no white arrow is added to custom points, while normal player locator rendering is preserved.
- With two clients, share publicly and privately; verify only the chosen private recipient gets an actionable token and imported copies remain independent.
- Restart the disposable server and confirm personal names, owner UUIDs, coordinates, visibility, colors, ranges, and styles survive. Previously issued share tokens should expire.
- Check the production jar with the target instance's other mods in a disposable client before installing it. Restart the real instance fully after installation.

## Verified release 1.2.0+26.3

The full GUI workflow passed on a real Fabric 26.3 client using copies of the target instance's installed mods. Java build checks, dedicated-server checks, and login command serialization passed. Reconnecting after a server restart verified saved ownership, readable names, coordinates, colors, styles, and End separation, and confirmed receipt of native locator packets.

A separate real client, `WaypointPeer`, began with an empty personal list while the sender already had saved points. Public and private shares each delivered a formatted message and an actionable Add command; importing produced independent personal copies and collision suffixes. Server checks rejected private-token use by the sender and confirmed that recipient imports did not alter the sender's list. To repeat this check, run the server with `-Dwaypoint.test.restart=true -Dwaypoint.test.peerserver=true`, the main client with `-Dwaypoint.test.restart=true`, and the second client with `-Dwaypoint.test.peer=true --username WaypointPeer` in a fresh disposable peer directory.

Run the main client with `-Dwaypoint.test.render=true` after creating the fixture points to repeat just the locator screenshots. Native locator packets must exist or this check fails. The cyan fixture icon visibly grew between distant, midpoint and near captures and remained visible without elevation arrows at steep upward/downward camera pitches. Screenshot checks disabled Dynamic FPS's background throttling only in the disposable render client; the full GUI compatibility run included Dynamic FPS. Real N/B key presses were also checked on the virtual display.

## Editor screenshots

The separate integration jar also has a screenshot-only mode, `-Dwaypoint.capture.editor=true`. It opens the editor, RGB color picker, and marker shape chooser in a disposable client and captures `editor`, `color-picker`, `icon-styles`, `icon-styles-bowtie`, and `editor-bowtie`. It does not save or edit world waypoints and does not run the integration checks. The production jar excludes this driver.
