package dev.skycraft.client;

import dev.skycraft.SkyCraft;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPreset;

/** Opens (or creates) the void "mirror" world automatically once Skyrim is connected. */
public final class MirrorWorld {
	private static final ResourceKey<WorldPreset> PRESET =
		ResourceKey.create(Registries.WORLD_PRESET, ResourceLocation.fromNamespaceAndPath(SkyCraft.MOD_ID, "mirror"));
	private static boolean attempted;
	private static long lastLog;
	// A configured localhost is a convenience for the host, not a requirement. If its dedicated
	// server is offline, skip it for the rest of this Minecraft session and use the local world.
	private static boolean skipConfiguredLocal;
	private static @org.jspecify.annotations.Nullable String attemptedJoin;
	private static boolean attemptedConfiguredJoin;
	private static long configuredRetryAt;
	private static int configuredFailures;
	// /join: a friend's world for this session (the e4mc link their "Open to LAN" shows); null: our own.
	private static @org.jspecify.annotations.Nullable String sessionJoin;
	// Shown in chat once the player is in a world again (why they're back in their own, ...).
	private static @org.jspecify.annotations.Nullable String pendingNote;

	private MirrorWorld() {
	}

	/**
	 * The address in config/skycraft.properties ({@code join=abc-def.e4mc.link}), if any. Written
	 * with the template below the first time, so there's something to fill in.
	 */
	private static @org.jspecify.annotations.Nullable String joinAddress(Minecraft minecraft) {
		java.nio.file.Path file = minecraft.gameDirectory.toPath().resolve("config").resolve("skycraft.properties");
		java.util.Properties props = new java.util.Properties();
		try {
			if (!java.nio.file.Files.exists(file)) {
				java.nio.file.Files.createDirectories(file.getParent());
				java.nio.file.Files.writeString(file, """
					# SkyCraft
					# To play in a friend's world instead of your own: put their address after join=
					# (the link e4mc shows them when they open their world to LAN), then restart Minecraft.
					join=
					""");
			}
			try (var in = java.nio.file.Files.newBufferedReader(file)) {
				props.load(in);
			}
		} catch (java.io.IOException e) {
			SkyCraft.LOG.warn("SkyCraft: couldn't read {}", file, e);
			return null;
		}
		String join = props.getProperty("join", "").trim();
		return join.isEmpty() ? null : join;
	}

	/** /join: leave this world and play in a friend's (their e4mc link, or any server address). */
	public static void joinFriend(Minecraft minecraft, String link) {
		// People paste all sorts: "https://abc-def.e4mc.link/", " abc-def.e4mc.link ".
		String address = link.trim().replaceFirst("^[A-Za-z]+://", "").replaceAll("/+$", "");
		if (address.isEmpty()) {
			return;
		}
		SkyCraft.LOG.info("SkyCraft: /join {}", address);
		sessionJoin = address;
		leaveWorld(minecraft);
	}

	/** The friend's world we're in (the address we joined), or null in our own. */
	public static @org.jspecify.annotations.Nullable String friendAddress(Minecraft minecraft) {
		if (sessionJoin != null) {
			return sessionJoin;
		}
		var server = minecraft.isLocalServer() ? null : minecraft.getCurrentServer();
		return server != null ? server.ip : null;
	}

	/** /leave: back to our own world. */
	public static void leaveFriend(Minecraft minecraft) {
		if (sessionJoin == null) {
			minecraft.gui.getChat().addMessage(net.minecraft.network.chat.Component.literal("You're already in your own world."));
			return;
		}
		SkyCraft.LOG.info("SkyCraft: /leave {}", sessionJoin);
		sessionJoin = null;
		pendingNote = "Back in your own world.";
		leaveWorld(minecraft);
	}

	private static void leaveWorld(Minecraft minecraft) {
		attempted = false;
		minecraft.disconnect();
		minecraft.setScreen(new TitleScreen());  // openWhenReady takes it from the title screen
	}

	/** Every client tick: a note for the player once they're in a world again. */
	public static void tick(Minecraft minecraft) {
		if (minecraft.player != null) {
			configuredRetryAt = 0;
			configuredFailures = 0;
		}
		if (pendingNote != null && minecraft.player != null) {
			minecraft.gui.getChat().addMessage(net.minecraft.network.chat.Component.literal(pendingNote));
			pendingNote = null;
		}
	}

	public static void openWhenReady(Minecraft minecraft) {
		// Couldn't reach a friend's world, or it closed under us: back to our own, and say why.
		if (minecraft.screen instanceof net.minecraft.client.gui.screens.DisconnectedScreen && minecraft.level == null) {
			boolean localServerUnavailable = attemptedConfiguredJoin && isLocalAddress(attemptedJoin);
			boolean configuredRemoteUnavailable = attemptedConfiguredJoin && !localServerUnavailable;
			if (localServerUnavailable) {
				skipConfiguredLocal = true;
				pendingNote = "The local dedicated server isn't running. You're using your own SkyCraft world.";
				SkyCraft.LOG.info("SkyCraft: local server unavailable; opening the local mirror world instead");
			} else if (configuredRemoteUnavailable) {
				configuredFailures++;
				long delaySeconds = Math.min(60L, 15L << Math.min(configuredFailures - 1, 2));
				configuredRetryAt = System.currentTimeMillis() + delaySeconds * 1000L;
				SkyCraft.LOG.warn("SkyCraft: connection to {} failed; retrying in {} seconds (attempt {})",
					attemptedJoin, delaySeconds, configuredFailures + 1);
			} else {
				pendingNote = sessionJoin != null
					? "Couldn't stay in " + sessionJoin + " (check the link, and that your friend's world is still open to LAN). You're back in your own world."
					: "Disconnected. You're back in your own world.";
				SkyCraft.LOG.info("SkyCraft: disconnected; back to the mirror world");
			}
			sessionJoin = null;
			attemptedJoin = null;
			attemptedConfiguredJoin = false;
			attempted = false;
			minecraft.setScreen(new TitleScreen());
			return;
		}
		if (attempted && minecraft.level == null && minecraft.screen != null && System.currentTimeMillis() - lastLog > 5000) {
			lastLog = System.currentTimeMillis();
			SkyCraft.LOG.info("SkyCraft: still not in the mirror world; current screen {}", minecraft.screen.getClass().getName());
		}
		if (attempted || minecraft.level != null || minecraft.getOverlay() != null) {
			return;
		}
		if (System.currentTimeMillis() < configuredRetryAt) {
			return;
		}
		// Wait for the menu to settle on the title screen; skip any first-launch prompts in front of it.
		if (!(minecraft.screen instanceof TitleScreen)) {
			if (minecraft.screen != null && System.currentTimeMillis() - lastLog > 5000) {
				lastLog = System.currentTimeMillis();
				SkyCraft.LOG.info("SkyCraft: waiting on screen {} before opening the mirror world", minecraft.screen.getClass().getName());
			}
			if (minecraft.screen == null || minecraft.screen.getClass().getName().contains("Onboarding")) {
				minecraft.setScreen(new TitleScreen());
			}
			return;
		}
		TitleScreen title = (TitleScreen) minecraft.screen;
		attempted = true;
		// Multiplayer: join a friend's world (their e4mc link, or any server address) instead.
		String configuredJoin = skipConfiguredLocal ? null : joinAddress(minecraft);
		String join = sessionJoin != null ? sessionJoin : configuredJoin;
		if (join != null) {
			attemptedJoin = join;
			attemptedConfiguredJoin = sessionJoin == null;
			SkyCraft.LOG.info("SkyCraft: joining {}", join);
			pendingNote = "Joined " + join + ". Type /leave to go back to your own world.";
			net.minecraft.client.gui.screens.ConnectScreen.startConnecting(title, minecraft, net.minecraft.client.multiplayer.resolver.ServerAddress.parseString(join),
				new net.minecraft.client.multiplayer.ServerData("SkyCraft", join, net.minecraft.client.multiplayer.ServerData.Type.OTHER), false, null);
			return;
		}
		if (minecraft.getLevelSource().levelExists(SkyCraft.WORLD_NAME)) {
			SkyCraft.LOG.info("SkyCraft: opening mirror world");
			minecraft.createWorldOpenFlows().openWorld(SkyCraft.WORLD_NAME, () -> minecraft.setScreen(title));
			return;
		}
		SkyCraft.LOG.info("SkyCraft: creating mirror world");
		LevelSettings settings = new LevelSettings(
			SkyCraft.WORLD_NAME,
			GameType.SURVIVAL,
			false,
			Difficulty.NORMAL,
			true,
			new GameRules(),
			WorldDataConfiguration.DEFAULT
		);
		minecraft.createWorldOpenFlows().createFreshLevel(
			SkyCraft.WORLD_NAME,
			settings,
			new WorldOptions(0L, false, false),
			registries -> registries.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(PRESET).value().createWorldDimensions(),
			title
		);
	}

	private static boolean isLocalAddress(@org.jspecify.annotations.Nullable String address) {
		if (address == null) {
			return false;
		}
		String value = address.trim().toLowerCase(java.util.Locale.ROOT);
		return value.equals("localhost") || value.startsWith("localhost:") ||
			value.equals("127.0.0.1") || value.startsWith("127.0.0.1:") ||
			value.equals("::1") || value.equals("[::1]") || value.startsWith("[::1]:");
	}
}
