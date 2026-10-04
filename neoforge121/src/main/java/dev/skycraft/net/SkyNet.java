package dev.skycraft.net;

import dev.skycraft.SkyCraft;
import dev.skycraft.combat.SkyCombat;
import dev.skycraft.world.SkyDig;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Multiplayer: every player has their own Skyrim, talking to their own Minecraft client. The host's
 * Skyrim reaches the host's integrated server through shared memory; a guest's Skyrim reaches the
 * host's server through these packets instead.
 */
public final class SkyNet {
	private static final int ACTOR_BYTES = 64;
	private static final int MAX_ACTORS = 128;
	private static final List<dev.skycraft.link.SkyLink.Actor> CLIENT_ACTORS = new ArrayList<>();
	private static int nextActorSyncTick;
	private static int nextTerrainSyncTick;
	private static int nextTimeSyncTick;
	private static long lastClientWorldTick = Long.MIN_VALUE;
	private static final dev.skycraft.link.SkyLink.SkyState CLIENT_SKY = new dev.skycraft.link.SkyLink.SkyState();
	private static UUID timeLeader;

	private SkyNet() {
	}

	/** Guest -> server: the guest's Skyrim hit them (as proto::InputEvent kInHurt). */
	public record Hurt(int kind, float skyrimDamage, int attackerFormId, int flags) implements CustomPacketPayload {
		public static final Type<Hurt> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyCraft.MOD_ID, "hurt"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Hurt> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Hurt::kind,
			ByteBufCodecs.FLOAT, Hurt::skyrimDamage,
			ByteBufCodecs.INT, Hurt::attackerFormId,
			ByteBufCodecs.VAR_INT, Hurt::flags,
			Hurt::new
		);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Server -> guest: the guest died in Minecraft, so their Skyrim player dies too. */
	public record Died(int attackerFormId) implements CustomPacketPayload {
		public static final Type<Died> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyCraft.MOD_ID, "died"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Died> CODEC = StreamCodec.composite(ByteBufCodecs.INT, Died::attackerFormId, Died::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Client -> dedicated server: this player's nearby Skyrim actors. */
	public record ActorSync(byte[] data) implements CustomPacketPayload {
		public static final Type<ActorSync> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyCraft.MOD_ID, "actor_sync"));
		public static final StreamCodec<RegistryFriendlyByteBuf, ActorSync> CODEC = StreamCodec.of(
			(buf, payload) -> buf.writeByteArray(payload.data),
			buf -> new ActorSync(buf.readByteArray(ACTOR_BYTES * MAX_ACTORS))
		);
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	/** Dedicated server -> owning client: Minecraft hit one of that client's Skyrim actors. */
	public record HitActor(int formId, float damage, float pushX, float pushZ, float pushStrength, int flags, int weapon) implements CustomPacketPayload {
		public static final Type<HitActor> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyCraft.MOD_ID, "hit_actor"));
		public static final StreamCodec<RegistryFriendlyByteBuf, HitActor> CODEC = StreamCodec.of(
			(buf, p) -> { buf.writeInt(p.formId); buf.writeFloat(p.damage); buf.writeFloat(p.pushX); buf.writeFloat(p.pushZ); buf.writeFloat(p.pushStrength); buf.writeVarInt(p.flags); buf.writeVarInt(p.weapon); },
			buf -> new HitActor(buf.readInt(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readVarInt(), buf.readVarInt())
		);
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	/** Client -> dedicated server: voxelized Skyrim ground that mobs and items can stand on. */
	public record TerrainSync(BlockPos center, byte[] data) implements CustomPacketPayload {
		public static final Type<TerrainSync> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyCraft.MOD_ID, "terrain_sync"));
		public static final StreamCodec<RegistryFriendlyByteBuf, TerrainSync> CODEC = StreamCodec.of(
			(buf, p) -> { BlockPos.STREAM_CODEC.encode(buf, p.center); buf.writeByteArray(p.data); },
			buf -> new TerrainSync(BlockPos.STREAM_CODEC.decode(buf), buf.readByteArray(76 * 4096))
		);
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	/** Time leader -> server: Skyrim's current hour (0.0 through 24.0). */
	public record TimeSync(float gameHour) implements CustomPacketPayload {
		public static final Type<TimeSync> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyCraft.MOD_ID, "time_sync"));
		public static final StreamCodec<RegistryFriendlyByteBuf, TimeSync> CODEC = StreamCodec.composite(ByteBufCodecs.FLOAT, TimeSync::gameHour, TimeSync::new);
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	/** Client -> server: the player hit Skyrim's geometry in this cell (SkyDig.open). */
	public record DigOpen(int world, BlockPos pos, int material) implements CustomPacketPayload {
		public static final Type<DigOpen> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyCraft.MOD_ID, "dig_open"));
		public static final StreamCodec<RegistryFriendlyByteBuf, DigOpen> CODEC = StreamCodec.composite(
			ByteBufCodecs.INT, DigOpen::world,
			BlockPos.STREAM_CODEC, DigOpen::pos,
			ByteBufCodecs.VAR_INT, DigOpen::material,
			DigOpen::new
		);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Client -> server: cells around a broken dug block that are inside Skyrim's geometry (SkyDig.reveal). */
	public record DigReveal(int world, List<BlockPos> cells, List<Integer> materials) implements CustomPacketPayload {
		public static final Type<DigReveal> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyCraft.MOD_ID, "dig_reveal"));
		public static final StreamCodec<RegistryFriendlyByteBuf, DigReveal> CODEC = StreamCodec.composite(
			ByteBufCodecs.INT, DigReveal::world,
			BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list(64)), DigReveal::cells,
			ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(64)), DigReveal::materials,
			DigReveal::new
		);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public static void init(IEventBus modBus) {
		modBus.addListener(SkyNet::registerPayloads);
	}

	private static void registerPayloads(RegisterPayloadHandlersEvent event) {
		var registrar = event.registrar("4");
		registrar.playToServer(TimeSync.TYPE, TimeSync.CODEC, (payload, context) -> syncTime((ServerPlayer) context.player(), payload.gameHour()));
		registrar.playToServer(TerrainSync.TYPE, TerrainSync.CODEC, (payload, context) -> {
			ServerPlayer player = (ServerPlayer) context.player();
			dev.skycraft.world.SkyCollision.applyRemote(player.getUUID(), payload.center(), 24, 24, payload.data());
		});
		registrar.playToServer(ActorSync.TYPE, ActorSync.CODEC, (payload, context) -> {
			ServerPlayer player = (ServerPlayer) context.player();
			SkyCombat.syncPlayer(player, decodeActors(payload.data()));
		});
		registrar.playToServer(DigOpen.TYPE, DigOpen.CODEC, (payload, context) -> {
			ServerPlayer player = (ServerPlayer) context.player();
			SkyDig.open(player, payload.world(), payload.pos(), payload.material());
		});
		registrar.playToServer(DigReveal.TYPE, DigReveal.CODEC, (payload, context) -> {
			ServerPlayer player = (ServerPlayer) context.player();
			int[] materials = payload.materials().stream().mapToInt(Integer::intValue).toArray();
			SkyDig.reveal(player, payload.world(), payload.cells(), materials);
		});
		registrar.playToServer(Hurt.TYPE, Hurt.CODEC, (payload, context) -> {
			ServerPlayer player = (ServerPlayer) context.player();
			// A hit's worth of damage, whatever the guest's client claims (friends only, but still).
			float damage = Math.max(0.0F, Math.min(payload.skyrimDamage(), 10000.0F));
			SkyCombat.hurtPlayer(player, payload.kind(), damage, payload.attackerFormId(), payload.flags());
		});
		registrar.playToClient(Died.TYPE, Died.CODEC, (payload, context) -> {
			if (dev.skycraft.link.SkyLink.active()) {
				dev.skycraft.link.SkyLink.pushEvent(dev.skycraft.link.Proto.EV_PLAYER_DIED, payload.attackerFormId(), 0, 0, 0, 0, 0);
			}
		});
		registrar.playToClient(HitActor.TYPE, HitActor.CODEC, (payload, context) -> {
			if (dev.skycraft.link.SkyLink.active()) {
				dev.skycraft.link.SkyLink.pushEvent(dev.skycraft.link.Proto.EV_HIT_ACTOR, payload.formId(), payload.damage(), payload.pushX(), payload.pushZ(),
					payload.pushStrength(), payload.flags(), payload.weapon());
			}
		});
	}

	/** Sends the local Skyrim actor table to a remote/dedicated server five times per second. */
	public static void clientTick(Minecraft minecraft) {
		if (minecraft.getConnection() == null || minecraft.level == null) {
			nextActorSyncTick = nextTerrainSyncTick = nextTimeSyncTick = 0; lastClientWorldTick = Long.MIN_VALUE; return;
		}
		long tick = minecraft.level.getGameTime();
		if (tick < lastClientWorldTick) nextActorSyncTick = nextTerrainSyncTick = nextTimeSyncTick = 0;
		lastClientWorldTick = tick;
		if (tick >= nextTimeSyncTick && dev.skycraft.link.SkyLink.readSkyState(CLIENT_SKY) && CLIENT_SKY.inGame()) {
			nextTimeSyncTick = (int) tick + 20;
			var integrated = minecraft.getSingleplayerServer();
			if (integrated != null) integrated.execute(() -> applyTime(integrated, CLIENT_SKY.gameHour));
			else PacketDistributor.sendToServer(new TimeSync(CLIENT_SKY.gameHour));
		}
		if (minecraft.getSingleplayerServer() != null) return;
		if (tick >= nextActorSyncTick) {
			nextActorSyncTick = (int) tick + 4;
			if (dev.skycraft.link.SkyLink.readActors(CLIENT_ACTORS)) PacketDistributor.sendToServer(new ActorSync(encodeActors(CLIENT_ACTORS)));
		}
		if (tick >= nextTerrainSyncTick && minecraft.player != null && dev.skycraft.world.SkyCollision.active()) {
			nextTerrainSyncTick = (int) tick + 40;
			BlockPos center = minecraft.player.blockPosition();
			PacketDistributor.sendToServer(new TerrainSync(center, dev.skycraft.world.SkyCollision.snapshotAround(center, 24, 24, 4096)));
		}
	}

	private static void syncTime(ServerPlayer player, float hour) {
		var server=player.getServer(); if(server==null)return;
		ServerPlayer old=timeLeader==null?null:server.getPlayerList().getPlayer(timeLeader);
		if(old==null || player.getUUID().equals(timeLeader) || (player.hasPermissions(2) && !old.hasPermissions(2))) {
			if(!player.getUUID().equals(timeLeader)) SkyCraft.LOG.info("SkyCraft: {} is the Skyrim time leader", player.getName().getString());
			timeLeader=player.getUUID();
		}
		if(player.getUUID().equals(timeLeader)) applyTime(server,hour);
	}

	private static void applyTime(net.minecraft.server.MinecraftServer server, float hour) {
		if(!Float.isFinite(hour))return; hour=((hour%24.0F)+24.0F)%24.0F;
		var level=server.overworld(); long current=level.getDayTime(), day=Math.floorDiv(current,24000L)*24000L;
		long within=Math.round((((hour-6.0F)+24.0F)%24.0F)*1000.0F);
		long target=day+within;
		while(target-current>12000L)target-=24000L;
		while(current-target>12000L)target+=24000L;
		level.setDayTime(target);
	}

	public static void playerLeft(UUID player) { if(player.equals(timeLeader)) timeLeader=null; }

	private static byte[] encodeActors(List<dev.skycraft.link.SkyLink.Actor> actors) {
		int count = Math.min(actors.size(), MAX_ACTORS);
		ByteBuffer out = ByteBuffer.allocate(count * ACTOR_BYTES).order(ByteOrder.LITTLE_ENDIAN);
		for (int i = 0; i < count; i++) {
			var a = actors.get(i);
			out.putInt(a.formId()).putInt(a.flags()).putFloat(a.x()).putFloat(a.y()).putFloat(a.z()).putFloat(a.yaw())
				.putFloat(a.width()).putFloat(a.height()).putFloat(a.healthFrac()).putInt(a.level());
			byte[] name = a.name().getBytes(StandardCharsets.UTF_8);
			out.put(name, 0, Math.min(name.length, 23));
			out.position((i + 1) * ACTOR_BYTES);
		}
		return out.array();
	}

	private static List<dev.skycraft.link.SkyLink.Actor> decodeActors(byte[] data) {
		int count = Math.min(data.length / ACTOR_BYTES, MAX_ACTORS);
		ByteBuffer in = ByteBuffer.wrap(data, 0, count * ACTOR_BYTES).order(ByteOrder.LITTLE_ENDIAN);
		List<dev.skycraft.link.SkyLink.Actor> actors = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			int start = i * ACTOR_BYTES;
			int form = in.getInt(), flags = in.getInt();
			float x=in.getFloat(), y=in.getFloat(), z=in.getFloat(), yaw=in.getFloat(), width=in.getFloat(), height=in.getFloat(), health=in.getFloat();
			int level=in.getInt(); byte[] nameBytes=new byte[24]; in.get(nameBytes); int n=0; while(n<nameBytes.length && nameBytes[n]!=0)n++;
			actors.add(new dev.skycraft.link.SkyLink.Actor(form, flags, x, y, z, yaw, width, height, health, level, new String(nameBytes, 0, n, StandardCharsets.UTF_8)));
			in.position(start + ACTOR_BYTES);
		}
		return actors;
	}

	/** True if this player plays on this machine (their Skyrim is on the shared-memory link). */
	public static boolean isHost(ServerPlayer player) {
		var server = player.level().getServer();
		return server != null && server.isSingleplayerOwner(player.getGameProfile());
	}
}
