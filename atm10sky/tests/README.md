# Streamed terrain collision regression check

Use only a disposable, loopback-only ATM10Sky test server with no players. This
probe adds temporary forced chunks and a cow, then stops the server after 100
ticks. It is deliberately excluded from release packages.

Compile `CollisionFixture.java` with Java 21 (`--enable-preview --release 21`)
against the mapped Minecraft 1.21.1 jar, the current SkyCraft jar, and fastutil.
Package its class files and `resources/META-INF/neoforge.mods.toml` in a
diagnostic-only jar and place that jar in the isolated server's mods folder.
The Java fixture supplies binary packet data without KubeJS's array converters;
it asserts that the ground cell exists before the movement check starts.

Copy `terrain-collision-probe.js` into that test server's `kubejs/server_scripts`
folder. Start once with the stock Lithium configuration, save the log, then run
`Fix-Terrain-Collision.ps1 -ConfigDirectory <test-server>/config` and start again.

The probe submits a real `SkyCollision.applyRemote` snapshot containing 81 air
cells with a streamed surface at height 101. It creates a cow at height 105 and
requests downward movement through Minecraft's entity collision pipeline. No
ordinary Minecraft floor blocks and no connected Skyrim client are used.

Search `logs/latest.log` for `SKYCRAFT_COLLISION_PROBE`. With the stock Lithium
collision replacements, the cow reaches height 95 and reports `FAIL`. With
the compatibility settings, the expected result is height 101, grounded,
and `PASS`. Remove the probe before using the server for interactive testing.
