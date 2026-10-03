package dev.skycraft.client;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.skycraft.combat.SkyCombat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class SkyCraftClient {
	private SkyCraftClient() {
	}

	public static void init(IEventBus modBus) {
		dev.skycraft.link.SkyLink.announceRunning();
		DiscordPresence.start();
		DestructionToggle.register();
		NeoForge.EVENT_BUS.addListener(SkyCraftClient::registerCommands);
		NeoForge.EVENT_BUS.addListener(SkyCraftClient::clientTick);
		NeoForge.EVENT_BUS.addListener(SkyCraftClient::clientJoined);
		modBus.addListener(SkyCraftClient::registerRenderers);
		dev.skycraft.world.SkyCollision.setSmoothCollider(
			e -> e instanceof net.minecraft.world.entity.player.Player && SkyClient.linked());
	}

	private static void registerCommands(RegisterClientCommandsEvent event) {
		event.getDispatcher().register(Commands.literal("join")
			.then(Commands.argument("link", StringArgumentType.greedyString()).executes(c -> {
				String link = StringArgumentType.getString(c, "link");
				c.getSource().sendSuccess(() -> Component.literal("Joining " + link.trim() + "..."), false);
				Minecraft.getInstance().execute(() -> MirrorWorld.joinFriend(Minecraft.getInstance(), link));
				return 1;
			})));
		event.getDispatcher().register(Commands.literal("leave").executes(c -> {
			Minecraft.getInstance().execute(() -> MirrorWorld.leaveFriend(Minecraft.getInstance()));
			return 1;
		}));
	}

	private static void clientTick(ClientTickEvent.Post event) {
		SkyClient.clientTick(Minecraft.getInstance());
	}

	private static void clientJoined(ClientPlayerNetworkEvent.LoggingIn event) {
		Minecraft minecraft = Minecraft.getInstance();
		String port = System.getenv("SKYCRAFT_LAN_PORT");
		var server = minecraft.getSingleplayerServer();
		if (port == null || port.isBlank() || server == null || server.isPublished()) {
			return;
		}
		minecraft.execute(() -> {
			if (System.getenv("SKYCRAFT_LAN_OFFLINE") != null) {
				server.setUsesAuthentication(false);
			}
			boolean ok = server.publishServer(null, false, Integer.parseInt(port.trim()));
			dev.skycraft.SkyCraft.LOG.info("SkyCraft: world opened to LAN on port {} ({}{})", port.trim(), ok ? "ok" : "FAILED",
				System.getenv("SKYCRAFT_LAN_OFFLINE") != null ? ", offline logins allowed" : "");
		});
	}

	private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
		event.registerEntityRenderer(SkyCombat.SKYRIM_ACTOR.get(), NoopRenderer::new);
	}
}
