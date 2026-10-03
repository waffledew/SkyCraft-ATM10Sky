# SkyCraft + ATM10 To the Sky 2.0.6

This compatibility package runs **All the Mods 10: To the Sky 2.0.6** as
SkyCraft's hidden Minecraft simulation. It supports a shared dedicated Minecraft
world while every player keeps their own Skyrim installation, save, NPCs, and
quests.

## What every player needs

- A legitimate copy of Skyrim Special Edition or Anniversary Edition.
- SKSE64 and Address Library installed for that Skyrim runtime.
- The normal SkyCraft 0.1.2 package installed with Vortex and launched once so
  its portable Prism Launcher exists in `%LOCALAPPDATA%\SkyCraft\Prism`.
- A legitimate Minecraft Java account signed into that Prism Launcher.
- ATM10 To the Sky **2.0.6** installed through CurseForge.

Do not share Minecraft accounts, Skyrim game files, or the complete ATM modpack.
Each player installs those from their official sources.

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
4. Start Skyrim through SKSE and choose **No: ATM10 To the Sky** in SkyCraft's
   profile prompt.

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
