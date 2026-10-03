# SkyCraft + ATM10 To the Sky 2.0.6

This project is an unofficial compatibility port inspired by and based on
[chasmlol/SkyCraft](https://github.com/chasmlol/SkyCraft). Its goal is a modded
Minecraft-in-Skyrim experience using **All the Mods 10: To the Sky 2.0.6**. It
supports a shared dedicated Minecraft world while every player keeps their own
Skyrim installation, save, NPCs, and quests. It is not affiliated with the ATM
team or the original SkyCraft author.

## What every player needs

- A legitimate copy of Skyrim Special Edition or Anniversary Edition.
- SKSE64 and Address Library installed for that Skyrim runtime.
- The normal SkyCraft 0.1.2 package installed with Vortex and launched once so
  its portable Prism Launcher exists in `%LOCALAPPDATA%\SkyCraft\Prism`.
- A legitimate Minecraft Java account signed into that Prism Launcher.
- ATM10 To the Sky **2.0.6** installed through CurseForge.

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

For a friend, the short version is: install the prerequisites above, install
the supplied Vortex patch, run `Install-Client.ps1`, then enter the host's
e4mc address when the installer asks for a server.

1. Install `SkyCraft-ATM10Sky-Vortex-Patch.zip` with Vortex after normal
   SkyCraft and let the patch win its two file conflicts.
2. Extract this compatibility package to any folder.
3. Right-click `Install-Client.ps1`, choose **Run with PowerShell**, and follow
   its prompts. The script finds the 2.0.6 CurseForge instance and creates an
   isolated Prism instance named `SkyCraft ATM10SKY`.
4. Start Skyrim through SKSE. This patch launches the `SkyCraft ATM10SKY`
   profile automatically; there is no vanilla/modded prompt.

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

## Dedicated server (recommended)

1. Download the official `ATM10SKY-2.0.6-server.zip` from the ATM team.
2. Run:

   ```powershell
   powershell -ExecutionPolicy Bypass -File .\Prepare-Server.ps1 -ServerZip "C:\path\ATM10SKY-2.0.6-server.zip" -Destination "C:\ATM10Sky-Server"
   ```

3. Run `startserver.bat` in the destination. On its first run, read and accept
   the Minecraft EULA when prompted.
4. Wait for `Done` and `Domain assigned: something.e4mc.link` in the server
   console. Send that e4mc address to friends.
5. On the host PC, put `join=localhost:25565` in the ATM10Sky instance's
   `.minecraft\config\skycraft.properties`. Friends put the e4mc address after
   `join=`, or type `/join something.e4mc.link` once their hidden client opens.

The e4mc name changes whenever the server restarts. Port forwarding is not
needed. The server and every client must use the same SkyCraft ATM10Sky jar.

The dedicated server saves the world, player inventories, FTB teams/quests, and
mod data in its server folder using Minecraft's normal autosave. Always type
`stop` in the server console before closing it. Back up the whole server folder
(especially `world`, `config`, `defaultconfigs`, and the player/FTB data) before
updates or experiments.

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

## New-player progression

Each player receives one modest first-join kit: iron armor, an iron sword,
pickaxe, axe and shovel, 16 cooked beef, and 16 torches. There are no diamond
tools, golden apples, or free building stacks. ATM10Sky's FTB quest interface
and mod guidebooks remain available normally, so progression still starts from
the pack's quests rather than an overpowered handout.

## Troubleshooting

- **Missing `skycraft:*` channels:** install the supplied SkyCraft jar in both
  the server and every client's `mods` folder.
- **Preview features are not enabled:** keep `--enable-preview` in both the
  client JVM arguments and server `user_jvm_args.txt`.
- **Invisible modded entities:** confirm Iris and Sodium remain disabled and
  `config\flywheel-client.toml` contains `backend = "flywheel:off"`.
- **`Invalid argument: connect` in `sun.nio.ch.PipeImpl`:** this Windows/JDK
  loopback problem is machine-specific. Use another Java 21 distribution first;
  the repository's `gradle-uds-workaround` is an advanced fallback.

## Tested

The dedicated server path was tested on Minecraft 1.21.1, NeoForge 21.1.250,
ATM10 To the Sky 2.0.6, and e4mc NeoForge 6.1.1. A SkyCraft client authenticated,
spawned in the empty SkyCraft mirror world, retained the ATM quest/mod support,
and reached `joined the game` with the server's matching mod channels.
