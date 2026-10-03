# SkyCraft ATM10Sky

SkyCraft ATM10Sky is an unofficial compatibility project inspired by and based
on [chasmlol/SkyCraft](https://github.com/chasmlol/SkyCraft). It adapts that
Minecraft-in-Skyrim idea to **All the Mods 10: To the Sky 2.0.6** on Minecraft
1.21.1 and NeoForge 21.1.250.

The goal is simple: play Skyrim while ATM10Sky supplies the blocks, inventory,
machines, mobs, rendering, and shared Minecraft progression. Each player still
has their own Skyrim world, NPCs, quests, and save file.

> This is an experimental fan project. It is not affiliated with the ATM team,
> Mojang, Microsoft, Bethesda, ZeniMax, or the original SkyCraft author. Back up
> both your Skyrim saves and Minecraft server before updating.

## Download

Use the newest package on the
[Releases page](https://github.com/waffledew/SkyCraft-ATM10Sky/releases).
The ZIP contains a double-click friend installer, a double-click server-address
changer, a friendly host server launcher, a short `START-HERE.txt`, the
Vortex patch, server preparation script, licenses, and a detailed setup guide.

It does not redistribute Skyrim, Minecraft, the complete ATM10Sky modpack, or
account credentials. Every player must obtain those from their official sources.

## How CurseForge connects to Skyrim

1. Install ATM10 To the Sky 2.0.6 through CurseForge.
2. `Install-Client.ps1` finds that installation and copies it into an isolated
   SkyCraft Prism Launcher profile.
3. The installer adds this project's NeoForge bridge and compatibility settings.
4. The Vortex patch installs the SKSE DLL and configures it to launch the
   `SkyCraft ATM10SKY` profile automatically.
5. Minecraft and its console run hidden. The NeoForge mod and SKSE DLL exchange the Minecraft
   world state with Skyrim while both games keep running their own logic.

The installer uses Windows environment paths, supports a custom CurseForge
location, asks for the server address, and selects a memory limit based on the
PC's installed RAM. Skyrim SE/AE makes this a Windows-only project, but it does
not assume a particular Windows username or install drive.

## Quick start

Every player first installs Skyrim SE/AE, matching SKSE64 and Address Library,
normal SkyCraft 0.1.2, Minecraft Java Edition, and ATM10 To the Sky 2.0.6. Then:

1. Install this release's Vortex patch after normal SkyCraft.
2. Double-click `INSTALL-ATM10SKY.cmd` and enter the server address when asked.
3. The host enters `localhost:25565`; friends enter the host's current
   `e4mc.link` address.
4. Start Skyrim through SKSE. Do not separately start CurseForge or Prism.
5. Stay on Skyrim's main menu while its SkyCraft panel advances through the
   Minecraft startup stages. Load a save after it reaches **100% Minecraft
   ready**.

The host may play without starting the dedicated server. The client tries
`localhost:25565` once and falls back to its own local SkyCraft mirror world when
the server is offline. Start the server first whenever shared multiplayer world
progress is wanted.

For each multiplayer session, the host starts the prepared server once, waits
for `Done`, and opens `SERVER-ADDRESS.txt`. Friends run
`CHANGE-SERVER-ADDRESS.cmd` if that address changed, then everyone launches
Skyrim through SKSE. The first ATM10Sky client load can take several minutes.
The percentage is stage-based rather than a count of every mod: 100% is only
shown after the bridge is connected and Minecraft reports that its world is
loaded.

## Multiplayer and saves

The recommended setup uses the official ATM10Sky 2.0.6 dedicated-server pack
plus the included `Prepare-Server.ps1`. e4mc supplies a public join address
without router port forwarding.

The dedicated server normally autosaves its world, player inventories, FTB
teams and quests, and mod data. Always enter `stop` in its console before closing
it, and back up the complete server folder before updates.

Treat the e4mc address like an invitation link: anyone who has it can attempt to
join while the server is running. Private hosts should enable Minecraft's
whitelist and add each friend's exact Java Edition username. All players must
stay on ATM10Sky 2.0.6 and the same SkyCraft release; do not update individual
mods on only one computer.

New players receive one balanced kit: iron armor, iron sword, pickaxe, axe and
shovel, 16 cooked beef, and 16 torches. ATM10Sky quests and guidebooks remain
available normally; no diamond gear or large free building kit is supplied.

## Setup guide

See [the ATM10Sky setup guide](atm10sky/README.md) for prerequisites, client and
server installation, joining friends, troubleshooting, and exact compatibility
details.

## Source layout

- `skse/` — Skyrim SKSE plugin and ATM10Sky launch configuration.
- `neoforge121/` — Minecraft 1.21.1 NeoForge bridge.
- `atm10sky/` — portable client/server installers and release builder.
- `protocol/` — shared-memory protocol used by the two sides.

## License and attribution

The project is distributed under the [MIT License](LICENSE). See
[THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md) for upstream attribution and
third-party components.
