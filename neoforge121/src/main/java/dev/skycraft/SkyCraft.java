package dev.skycraft;

import dev.skycraft.combat.SkyCombat;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(SkyCraft.MOD_ID)
public final class SkyCraft {
	public static final String MOD_ID = "skycraft";
	public static final String WORLD_NAME = "SkyCraft";
	public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);
	// Keep the old tag value so players who already received a kit are not given another one.
	private static final String STARTER_KIT_TAG = "skycraft_builder_kit";

	public SkyCraft(IEventBus modBus) {
		SkyCombat.init(modBus);
		dev.skycraft.net.SkyNet.init(modBus);
		dev.skycraft.world.SkyDig.init(modBus);
		dev.skycraft.world.SkyMobSpawner.init();
		dev.skycraft.world.SkyRespawn.init();
		NeoForge.EVENT_BUS.addListener(SkyCraft::configureServer);
		NeoForge.EVENT_BUS.addListener(SkyCraft::playerJoined);
		NeoForge.EVENT_BUS.addListener(SkyCraft::playerLeft);
		// Dedicated servers need SkyCraft's packet handlers and common gameplay code, but must
		// never resolve Minecraft client classes. Keep all client initialization behind the
		// physical-dist check so the same jar can be installed on both sides of multiplayer.
		if (FMLEnvironment.dist == Dist.CLIENT) {
			dev.skycraft.client.SkyCraftClient.init(modBus);
		}
	}

	private static void playerLeft(PlayerEvent.PlayerLoggedOutEvent event) {
		dev.skycraft.world.SkyCollision.removeRemote(event.getEntity().getUUID());
		dev.skycraft.net.SkyNet.playerLeft(event.getEntity().getUUID());
	}

	private static void playerJoined(PlayerEvent.PlayerLoggedInEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			giveStarterKit(player);
		}
	}

	/** The mirror world is a void that only exists to host the player; Skyrim drives time and spawning. */
	private static void configureServer(ServerStartedEvent event) {
		MinecraftServer server = event.getServer();
		GameRules rules = server.getGameRules();
		rules.getRule(GameRules.RULE_DAYLIGHT).set(false, server);
		rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
		rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(true, server);
		rules.getRule(GameRules.RULE_DOINSOMNIA).set(false, server);
		rules.getRule(GameRules.RULE_DO_PATROL_SPAWNING).set(false, server);
		rules.getRule(GameRules.RULE_DO_TRADER_SPAWNING).set(false, server);
		rules.getRule(GameRules.RULE_DISABLE_ELYTRA_MOVEMENT_CHECK).set(true, server);
		rules.getRule(GameRules.RULE_KEEPINVENTORY).set(false, server);
		rules.getRule(GameRules.RULE_DO_IMMEDIATE_RESPAWN).set(false, server);
		rules.getRule(GameRules.RULE_ANNOUNCE_ADVANCEMENTS).set(false, server);
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "time set noon");
		LOG.info("SkyCraft: mirror world configured");
		dev.skycraft.server.ServerAddressWriter.start(server);
	}

	/**
	 * Local multiplayer test guests (tools/fake_guest.py; named Guest, Guest2, ...) wear a random
	 * mix of iron and diamond armour, so they're easy to tell apart.
	 */
	private static void dressTestGuest(ServerPlayer player) {
		if (!player.getName().getString().startsWith("Guest")) {
			return;
		}
		var random = player.getRandom();
		EquipmentSlot[] slots = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };
		net.minecraft.world.item.Item[][] pieces = {
			{ Items.IRON_HELMET, Items.DIAMOND_HELMET },
			{ Items.IRON_CHESTPLATE, Items.DIAMOND_CHESTPLATE },
			{ Items.IRON_LEGGINGS, Items.DIAMOND_LEGGINGS },
			{ Items.IRON_BOOTS, Items.DIAMOND_BOOTS },
		};
		for (int i = 0; i < slots.length; i++) {
			player.setItemSlot(slots[i], new ItemStack(pieces[i][random.nextBoolean() ? 1 : 0]));
		}
		LOG.info("SkyCraft: dressed test guest {} in iron and diamond", player.getName().getString());
	}

	private static void giveStarterKit(ServerPlayer player) {
		if (player.getTags().contains(STARTER_KIT_TAG)) {
			return;
		}
		var book = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
			net.minecraft.resources.ResourceLocation.parse("ftbquests:book"));
		if (book == Items.AIR) {
			LOG.warn("SkyCraft: quest book unavailable; starter grant deferred for {}", player.getName().getString());
			return;
		}
		if (!player.getInventory().contains(new ItemStack(book))) {
			player.getInventory().add(new ItemStack(book));
		}
		player.addTag(STARTER_KIT_TAG);
		LOG.info("SkyCraft: gave quest-book-only starter kit to {}", player.getName().getString());
	}

	private static void equipIfEmpty(ServerPlayer player, EquipmentSlot slot, net.minecraft.world.item.Item item) {
		if (player.getItemBySlot(slot).isEmpty()) {
			player.setItemSlot(slot, new ItemStack(item));
		} else {
			player.getInventory().add(new ItemStack(item));
		}
	}
}
