# SkyCraft + ATM10 To the Sky 2.0.6

This project is an unofficial compatibility port inspired by and based on
[chasmlol/SkyCraft](https://github.com/chasmlol/SkyCraft). Its goal is a modded
Minecraft-in-Skyrim experience using **All the Mods 10: To the Sky 2.0.6**. It
supports a shared dedicated Minecraft world while every player keeps their own
Skyrim installation, save, NPCs, and quests. It is not affiliated with the ATM
team or the original SkyCraft author.

## What every player needs

**Current friend-test prerelease: bridge 0.1.2-atm10sky.21, protocol 6.**
Do not mix this with the older .16 release. Windows/Skyrim runtime 1.7.104.0
has been tested locally; older Skyrim runtimes and remote friend-to-host joining
are not certified. Use a backed-up Skyrim save for the first test.

- A legitimate copy of Skyrim Special Edition or Anniversary Edition.
- SKSE64 and Address Library installed for that Skyrim runtime.
- The normal SkyCraft 0.1.2 package installed with Vortex and launched once so
  its portable Prism Launcher exists in `%LOCALAPPDATA%\SkyCraft\Prism`.
- A legitimate Minecraft Java account signed into that Prism Launcher.
- ATM10 To the Sky **2.0.6** installed through CurseForge.

Use SKSE64 and Address Library builds that match the player's installed Skyrim
runtime. This package does not downgrade Skyrim or choose those versions. A PC
with at least 16 GB of system RAM is strongly recommended; 24 GB or more gives
Skyrim and ATM10Sky more breathing room when running together.

Minecraft must use **64-bit Java 21**, not Java 22/24/25: the bridge uses Java 21
preview classes. The generated Prism profile enables automatic Java selection;
verify its Java setting is version 21 if it reports a preview/class-version error.
The dedicated-server launcher rejects other Java major versions. The optional
Windows pipe repair is only for `Invalid argument: connect` in `PipeImpl`.

Do not share Minecraft accounts, Skyrim game files, or the complete ATM modpack.
Each player installs those from their official sources.

### Why this is a small installer instead of one giant bundle

An everything-included archive would redistribute Skyrim/SKSE and hundreds of
CurseForge mods whose licenses and download rules differ, and it still could not
include another person's Microsoft/Minecraft login. This release is therefore a
thin installer: friends obtain the official prerequisites once, then this script
assembles and configures them automatically. That is the closest practical,
shareable one-package setup without republishing other creators' files or
accounts.

## Client setup

For a friend, the short version is: open `START-HERE.txt`, install the supplied
Vortex patch, double-click `INSTALL-ATM10SKY.cmd`, then enter the host's e4mc
address when the installer asks for a server. No PowerShell knowledge is needed.

1. Install `SkyCraft-ATM10Sky-Vortex-Patch.zip` with Vortex after normal
   SkyCraft and let the patch win its file conflicts.
2. Extract this compatibility package to any folder.
3. Double-click `INSTALL-ATM10SKY.cmd` and follow its prompts. It runs the
   included PowerShell installer for the user, finds the 2.0.6 CurseForge
   instance, and creates an isolated Prism instance named `SkyCraft ATM10SKY`.
4. Start Skyrim through SKSE. This patch launches the `SkyCraft ATM10SKY`
   profile automatically; there is no vanilla/modded prompt.
5. Remain at Skyrim's main menu until the SkyCraft loading panel says
   **100% - Minecraft ready**, then load a Skyrim save.

### Which address to enter

- **Host PC:** `localhost:25565`
- **Friend's PC:** the current address from the host's `SERVER-ADDRESS.txt`, for
  example `example-name.na.e4mc.link`
- **Not ready yet:** leave it blank and run `CHANGE-SERVER-ADDRESS.cmd` later

Do not include `https://`, a slash, or surrounding quotation marks. Close Skyrim
before changing the address, then relaunch it through SKSE.

The host's localhost setting is optional at runtime: SkyCraft tries the dedicated
server once, then automatically opens the profile's normal local mirror world if
no server is running. It tries localhost again the next time Minecraft starts.
Friend/e4mc addresses remain multiplayer destinations and are not treated as
localhost fallbacks.

If the host restarts the server and receives a new e4mc address, close Skyrim,
double-click `CHANGE-SERVER-ADDRESS.cmd`, paste the new address, and launch Skyrim
again. The helper updates the generated Prism profile; no configuration file needs
to be opened manually.

The CurseForge download is the source copy of the official modpack. The
installer copies it into SkyCraft's portable Prism Launcher, adds this project's
NeoForge bridge, and writes the chosen server address. When SKSE starts Skyrim,
the SkyCraft DLL starts that Prism profile invisibly. The NeoForge mod then sends
Minecraft blocks, entities, inventory, lighting, and actions to the DLL for
Skyrim to display and interact with.

The installer uses Windows environment folders instead of a creator-specific
username, checks several common CurseForge locations, asks for a custom folder
when needed, and chooses a Minecraft memory limit from the PC's installed RAM.
Skyrim SE/AE and Vortex make this a Windows package; it is intended to tolerate
different Windows usernames, drives, and CurseForge library locations.

The installer keeps SkyblockBuilder and SkyGUIs enabled. It disables Iris and
Sodium because they replace rendering paths SkyCraft exports into Skyrim.
Flywheel is set to its compatible `off` backend; Create still works, but its
animated contraptions are rendered by Minecraft's fallback renderer.

Lithium stays installed, but its movement/intersection collision replacements
and empty-space collision shortcut are disabled in `config/lithium.properties`.
Those paths treat Skyrim terrain as empty Minecraft space and bypass SkyCraft's
collision hook. The settings are required on both client and server; other
Lithium optimizations remain enabled.

Minecraft's game window is removed from the Windows taskbar and normal Alt-Tab
list, then parked off-screen during play, so friends interact only with Skyrim.
It is not minimized or truly hidden because some GPU drivers throttle an OpenGL
window in those states and make SkyCraft stutter. Prism can appear for the
initial Microsoft sign-in or when a launch error needs attention; this is
intentional so account login and failures are not hidden from the player.

The first launch can take several minutes because ATM10Sky has hundreds of mods.
Do not repeatedly click SKSE or start a second Prism/Minecraft copy while it is
loading. If it never connects, close Skyrim and inspect Prism or the generated
profile's `.minecraft\logs\latest.log` for an account or mod-loading error.

Skyrim's main menu now shows a stage-based Minecraft loading percentage. It is
not a fabricated per-mod counter: it advances when the launcher starts, the Java
process appears, the SkyCraft shared-memory link connects, and the Minecraft
world becomes active. Only that final world-ready signal produces 100%. The
panel also reports Microsoft sign-in, missing-launcher, and launch-failure states
instead of leaving the player guessing.

## Dedicated server (recommended)

Only the host prepares and runs a dedicated server. Friends install the client
side only; they do not run `Prepare-Server.ps1` or `START-SKYCRAFT-SERVER.cmd`.

Hostile natural spawns use Minecraft's sky/block darkness check with the
Skyrim-synchronized clock. Outdoor daylight prevents new hostile spawns; Minecraft
light sources can prevent them at night. Skyrim visual lighting and roofs are not
Minecraft light-engine blocks, so their shadows are not used for this check.
Existing mobs are not removed simply because morning arrives.

### One-time host setup

1. Download the official `ATM10SKY-2.0.6-server.zip` from the ATM team.
2. Run:

   ```powershell
   powershell -ExecutionPolicy Bypass -File .\Prepare-Server.ps1 -ServerZip "C:\path\ATM10SKY-2.0.6-server.zip" -Destination "C:\ATM10Sky-Server"
   ```

3. Double-click `START-SKYCRAFT-SERVER.cmd` in the prepared destination. On its
   first run, read and accept the Minecraft EULA when prompted. The server starts
   when the host runs this file; it does not start automatically with Windows.
4. Wait for `Done` and `Domain assigned: something.e4mc.link` in the server
   console. The launcher also writes the current address and friend instructions
   to `SERVER-ADDRESS.txt` in the server folder. Send that address to friends.
5. On the host PC, put `join=localhost:25565` in the ATM10Sky instance's
   `.minecraft\config\skycraft.properties`. The installer already does this when
   the host enters `localhost:25565`. Friends can run
   `CHANGE-SERVER-ADDRESS.cmd` whenever the e4mc address changes.

### Starting each session

1. Double-click `START-SKYCRAFT-SERVER.cmd` once.
2. Wait for the console to show `Done`. ATM10Sky can take a few minutes.
3. Open `SERVER-ADDRESS.txt` beside the launcher. While starting, it says it is
   waiting; once e4mc connects, it contains the current public address.
4. Send that address privately to friends.
5. Friends close Skyrim, run `CHANGE-SERVER-ADDRESS.cmd`, paste the address, and
   relaunch Skyrim through SKSE. The host simply launches SKSE because its client
   uses `localhost:25565`.

If the launcher says a server is already listening on port 25565, use the
existing server. Do not start another copy. A second copy cannot open the same
world and will report a world-directory lock.

The e4mc name changes whenever the server restarts. Port forwarding is not
needed. The server and every client must use the same SkyCraft ATM10Sky jar.
The supplied launcher finds and verifies a 64-bit Java 21 runtime. Java 22 or
newer cannot run SkyCraft's Java 21 preview classes, even though the upstream
ATM server batch normally accepts any Java version numbered 21 or higher.
It also refuses to start if port 25565 already has a server listening and turns
off the upstream batch file's automatic crash-restart loop, preventing duplicate
servers from repeatedly fighting over the same world.

The dedicated server saves the world, player inventories, FTB teams/quests, and
mod data in its server folder using Minecraft's normal autosave. Always type
`stop` in the server console before closing it. Back up the whole server folder
(especially `world`, `config`, `defaultconfigs`, and the player/FTB data) before
updates or experiments.

### Private-server access

An e4mc name removes the need for port forwarding, but it is still an invitation
to the server while that server is online. For private play, enter these commands
in the server console, replacing the example names with exact Minecraft Java
usernames:

```text
whitelist on
whitelist add HostMinecraftName
whitelist add FriendMinecraftName
```

Use `whitelist list` to review access. Operators have powerful commands; grant
one only when needed with `op MinecraftName`, and remove it with
`deop MinecraftName`. For an intentionally public server, leave the whitelist
off and plan appropriate moderation and backups.

### Ending a session and backups

1. Have players close Skyrim normally and wait for them to disconnect.
2. Type `save-all flush` in the server console if you want an immediate save.
3. Type `stop` and wait for the console to finish. Do not close the window with
   the X button while the server is saving.
4. Periodically copy or archive the entire prepared server folder while the
   server is stopped. The `world` folder contains the world and player data;
   other mod and FTB data also lives elsewhere in the server folder, so backing
   up only `world` is less complete.

`Prepare-Server.ps1` selects `skycraft:mirror` as the server's empty shared
world, so it does not generate a separate starting sky island. SkyblockBuilder
and SkyGUIs must remain installed on both server and clients because NeoForge
requires their network channels to match during login. Clients therefore keep
the normal ATM10Sky menus and quest support without letting the skyblock preset
control the shared server world.

## Multiplayer model

- Minecraft blocks, inventories, machines, mobs, players, teams, and FTB quest
  progress live on the shared server.
- Skyrim NPCs, quests, locations, and save files stay local to each player.
- A friend joining the Minecraft server does not join the host's Skyrim quest
  instance.
- Skyrim save files are never transferred through the Minecraft server.
- Minecraft chat, blocks, machines, inventories, teams, and supported mod
  progression are shared; Skyrim NPC and quest decisions are not.

## New-player progression

### Death and respawning (experimental)

Minecraft owns death and inventory/XP or modded grave handling. Skyrim no longer
reloads a save on Minecraft death. Click Minecraft's Respawn button in the overlay.
A valid bed takes priority; a missing or obstructed bed falls back to the shared
spawn. The bridge records the Skyrim cell/worldspace alongside those positions,
loads that area, and holds/protects the player until terrain is ready, followed by
ten seconds of protection after the client explicitly confirms respawn terrain
readiness. Ordinary area updates do not end respawn protection. The first linked player standing on loaded terrain
establishes the shared spawn; an operator can stand at a preferred safe location
and use `/skycraftspawn set` to replace it. These records persist with the world.

This build uses networking protocol 6: all friends and the server need the new
SkyCraft jar, and each Skyrim needs the matching DLL. Beds set before this update
must be clicked again to record their Skyrim area. Use matching Skyrim world/cell
mods and load orders; a missing area ID is rejected rather than guessed.
Cross-cell travel and the complete death/respawn flow still need in-game testing.

Dropped items now use Minecraft's entity renderer and actual item model when
its geometry can be exported. This preserves block shapes, item thickness, and
custom models where supported. Unsupported models retain the old cube/icon
fallback rather than becoming invisible. Rendering still needs in-game checks.

Each player receives only an FTB quest book on first join. There is no free
armor, tool, food, torch, or building kit. Existing inventories are not cleared
by updating the mod; a fresh server world resets player and quest progress.

## Troubleshooting

- **`SERVER-ADDRESS.txt` still says waiting:** wait for the server's `Done`
  message. If it remains unchanged after ten minutes, search `logs\debug.log`
  for `Domain assigned` or an e4mc connection error.
- **World is locked / another process has locked the file:** another copy of the
  server is already running. Close the newly started copy and use the original.
- **Friend cannot connect after a restart:** e4mc usually assigned a new name.
  Send the new `SERVER-ADDRESS.txt`; the friend must run
  `CHANGE-SERVER-ADDRESS.cmd` while Skyrim is closed.
- **Friend sees "connecting too fast":** fully close Skyrim, Prism, and Minecraft,
  wait 60 seconds, then run `CHANGE-SERVER-ADDRESS.cmd` with the host's newest
  address and launch SKSE once. Current clients automatically back off failed
  remote connections (15, 30, then 60 seconds) instead of hammering e4mc.
- **Host sees one failed localhost connection with the server off:** this is
  expected. SkyCraft then opens the host's local mirror world instead of retrying
  forever. Start the dedicated server before SKSE when shared progress is wanted.
- **ATM10Sky seems frozen during first load:** give it several minutes and do
  not launch another copy. Stay on Skyrim's main menu until the loading panel
  reaches 100%. Check Prism only if the client never connects.
- **Minecraft appears briefly:** a short startup flash is possible. After the
  bridge initializes, its renderer is removed from the taskbar/normal Alt-Tab
  list and parked off-screen without GPU-throttling minimization.

- **Missing `skycraft:*` channels:** install the supplied SkyCraft jar in both
  the server and every client's `mods` folder.
- **Incompatible client / Please use NeoForge 21.1.250:** close Skyrim and
  Minecraft, run `UPDATE-ATM10SKY.cmd` from the current release, and start Skyrim
  through SKSE. The generated `SkyCraft ATM10SKY` Prism profile is required;
  launching the CurseForge source profile can use a different loader and omit
  the bridge. Character/terrain synchronization channels are part of the
  supplied SkyCraft jar, not a separate character-sync download.
- **Preview features are not enabled:** keep `--enable-preview` in both the
  client JVM arguments and server `user_jvm_args.txt`.
- **Class version `65.65535` versus `68.65535`:** the server accidentally used
  Java 24. Start it with `START-SKYCRAFT-SERVER.cmd`, which selects Java 21.
- **Invisible modded entities:** confirm Iris and Sodium remain disabled and
  `config\flywheel-client.toml` contains `backend = "flywheel:off"`.
- **Mobs fall through Skyrim terrain:** run `FIX-TERRAIN-COLLISION.cmd` while
  Minecraft and the server are closed. Leave the folder prompt blank to repair
  this PC's client, then run it again with the server folder path to repair the
  host's server. Restart both. It backs up the existing Lithium configuration
  and disables only the three collision options incompatible with streamed
  Skyrim ground. Terrain still needs an active Skyrim client nearby to supply it.
- **`Invalid argument: connect` in `sun.nio.ch.PipeImpl`:** this Windows/JDK
  loopback problem is machine-specific. Use another Java 21 distribution first;
  double-click `ENABLE-WINDOWS-PIPE-FIX.cmd` from this release for the optional
  bundled Java 21 fallback. Leave its folder prompt blank for the client; for
  the server, enter the prepared server folder. Close the affected games/server
  first. The helper backs up the configuration and uses this PC's own paths.

## Tested

The dedicated server path was tested on Minecraft 1.21.1, NeoForge 21.1.250,
ATM10 To the Sky 2.0.6, and e4mc NeoForge 6.1.1. A SkyCraft client authenticated,
spawned in the empty SkyCraft mirror world, retained the ATM quest/mod support,
and reached `joined the game` with the server's matching mod channels.

## Updating

Hosts stop the existing server and run `UPDATE-SKYCRAFT-SERVER.cmd` from the
new package, selecting the existing prepared 2.0.6 server folder. Do not rerun
server preparation over a populated server. The updater retains world/player/
quest data and ops/settings and backs up old jars. Update every client as well.

Existing clients close Skyrim and Minecraft, extract the new release, install
its Vortex patch, and double-click `UPDATE-ATM10SKY.cmd`. The updater preserves
saves, mod configurations, the chosen server address, and launcher preferences.
It backs up replaced bridge jars and launcher metadata inside the generated
profile's `skycraft-update-backups` folder, and pins NeoForge to 21.1.250.
Use `INSTALL-ATM10SKY.cmd` only for the first installation. Update the stopped
server's SkyCraft jar to the same release before friends reconnect.

Two Minecraft clients on one PC can test Minecraft networking and rendering,
but they do not reproduce two players each running Skyrim. End-to-end Skyrim
combat and terrain synchronization still require two independently linked
Skyrim clients. This project does not currently provide a supported dual-Skyrim
launcher for one PC.

Back up the stopped server before changing ATM10Sky, NeoForge, SkyCraft, or any
individual mod. Update the host and every friend together. A release built for a
different ATM10Sky version should be treated as incompatible until explicitly
tested; this guide and installer target ATM10 To the Sky 2.0.6 only.
