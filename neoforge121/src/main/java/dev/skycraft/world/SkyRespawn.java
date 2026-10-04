package dev.skycraft.world;

import dev.skycraft.SkyCraft;
import dev.skycraft.net.SkyNet;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerSetSpawnEvent;
import net.neoforged.neoforge.event.entity.player.PlayerRespawnPositionEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.HashMap;
import java.util.UUID;

/** Server-owned spawn selection; Skyrim area IDs accompany Minecraft positions. */
public final class SkyRespawn {
    private static final String CONTEXT = "skycraft_area";
    private static final String BED = "skycraft_bed_area";
    private static final String TARGET = "skycraft_respawn_area";
    private record Hold(int area, Vec3 position, int started) {}
    private static final HashMap<UUID, Hold> HOLDS = new HashMap<>();
    private static final HashMap<UUID, Integer> GRACE = new HashMap<>();
    private static final HashMap<UUID, Integer> READY = new HashMap<>();
    private SkyRespawn() {}

    public static void init() {
        NeoForge.EVENT_BUS.addListener(SkyRespawn::setSpawn);
        NeoForge.EVENT_BUS.addListener(SkyRespawn::choosePosition);
        NeoForge.EVENT_BUS.addListener(SkyRespawn::clonePlayer);
        NeoForge.EVENT_BUS.addListener(SkyRespawn::respawned);
        NeoForge.EVENT_BUS.addListener(SkyRespawn::holdPlayers);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.RegisterCommandsEvent event) -> {
            event.getDispatcher().register(net.minecraft.commands.Commands.literal("skycraftspawn")
                .requires(source -> source.hasPermission(2))
                .then(net.minecraft.commands.Commands.literal("set").executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    int area = player.getPersistentData().getInt(CONTEXT);
                    if (area == 0 || !contextFresh(player) || HOLDS.containsKey(player.getUUID())) {
                        context.getSource().sendFailure(net.minecraft.network.chat.Component.literal("Skyrim has not confirmed ready terrain yet, or a respawn is still in progress."));
                        return 0;
                    }
                    if (!supported(player)) {
                        context.getSource().sendFailure(net.minecraft.network.chat.Component.literal("Stand on loaded terrain or a solid Minecraft block; stop flying/jumping before setting spawn."));
                        return 0;
                    }
                    SpawnData saved = data(player);
                    saved.area = area; saved.position = player.position(); saved.yaw = player.getYRot(); saved.setDirty();
                    context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("Shared SkyCraft spawn saved. Beds still override it for each player."), true);
                    return 1;
                })));
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            HOLDS.remove(event.getEntity().getUUID()); GRACE.remove(event.getEntity().getUUID()); READY.remove(event.getEntity().getUUID());
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStartedEvent event) -> {
            HOLDS.clear(); GRACE.clear(); READY.clear();
        });
    }

    public static void updateContext(ServerPlayer player, int area) {
        if (area == 0 || player.isDeadOrDying()) return;
        READY.put(player.getUUID(), player.getServer().getTickCount());
        player.getPersistentData().putInt(CONTEXT, area);
        SpawnData data = data(player);
        // The first linked, terrain-ready player establishes the shared fallback spawn.
        if (data.area == 0 && supported(player) && !player.isSpectator()) {
            data.area = area;
            data.position = player.position();
            data.yaw = player.getYRot();
            data.setDirty();
            SkyCraft.LOG.info("SkyCraft: shared spawn established in Skyrim area {} at {}", area, data.position);
        }
    }

    private static boolean contextFresh(ServerPlayer player) {
        return player.getServer().getTickCount() - READY.getOrDefault(player.getUUID(), -10000) < 60;
    }

    private static boolean supported(ServerPlayer player) {
        if (player.getAbilities().flying) return false;
        int x = net.minecraft.util.Mth.floor(player.getX()), y = net.minecraft.util.Mth.floor(player.getY()), z = net.minecraft.util.Mth.floor(player.getZ());
        double surface = SkyCollision.surfaceY(x, z, y - 2, y);
        boolean terrain = supportedSurface(player.getY(), surface);
        return terrain || !player.serverLevel().noCollision(player, player.getBoundingBox().move(0, -0.15, 0));
    }

    static boolean supportedSurface(double feet, double surface) {
        return Double.isFinite(surface) && Math.abs(feet - surface) <= 0.6;
    }

    public static void finishRespawn(ServerPlayer player, int token) {
        Hold hold = HOLDS.get(player.getUUID());
        if (hold == null || hold.started != token) return;
        HOLDS.remove(player.getUUID());
        GRACE.put(player.getUUID(), player.getServer().getTickCount() + 200);
        SkyCraft.LOG.info("SkyCraft: {} respawn terrain ready; ten seconds of protection", player.getName().getString());
    }

    private static SpawnData data(ServerPlayer player) {
        return player.getServer().overworld().getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(SpawnData::new, SpawnData::load), "skycraft_spawn");
    }

    private static void setSpawn(PlayerSetSpawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.isCanceled()) return;
        CompoundTag persistent = player.getPersistentData();
        if (event.getNewSpawn() == null) { persistent.remove(BED); return; }
        int area = persistent.getInt(CONTEXT);
        if (area == 0) return;
        CompoundTag bed = new CompoundTag();
        bed.putInt("area", area);
        bed.putLong("pos", event.getNewSpawn().asLong());
        bed.putString("dimension", event.getSpawnLevel().location().toString());
        persistent.put(BED, bed);
    }

    private static void choosePosition(PlayerRespawnPositionEvent event) {
        if (event.isFromEndFight() || !(event.getEntity() instanceof ServerPlayer player)) return;
        CompoundTag persistent = player.getPersistentData();
        CompoundTag bed = persistent.getCompound(BED);
        var original = event.getOriginalDimensionTransition();
        var spawn = player.getRespawnPosition();
        boolean validBed = hasValidBed(bed, spawn, player.getRespawnDimension(), original.missingRespawnBlock());
        if (validBed) {
            persistent.putInt(TARGET, bed.getInt("area"));
            return; // Keep Minecraft's own bed/obstruction selection and position.
        }
        SpawnData data = data(player);
        if (data.area == 0) return;
        persistent.putInt(TARGET, data.area);
        event.setDimensionTransition(new DimensionTransition(player.getServer().overworld(),
            data.position, Vec3.ZERO, data.yaw, 0, DimensionTransition.DO_NOTHING));
    }

    static boolean hasValidBed(CompoundTag bed, net.minecraft.core.BlockPos spawn,
            net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension, boolean missing) {
        return !missing && spawn != null && bed.getInt("area") != 0
            && bed.getLong("pos") == spawn.asLong()
            && bed.getString("dimension").equals(dimension.location().toString());
    }

    private static void clonePlayer(PlayerEvent.Clone event) {
        for (String key : new String[] {CONTEXT, BED, TARGET}) {
            var value = event.getOriginal().getPersistentData().get(key);
            if (value != null) event.getEntity().getPersistentData().put(key, value.copy());
        }
    }

    private static void respawned(PlayerEvent.PlayerRespawnEvent event) {
        if (event.isEndConquered() || !(event.getEntity() instanceof ServerPlayer player)) return;
        int area = player.getPersistentData().getInt(TARGET);
        if (area == 0) return;
        player.getPersistentData().remove(TARGET);
        player.getPersistentData().putInt(CONTEXT, area);
        player.invulnerableTime = 100;
        HOLDS.put(player.getUUID(), new Hold(area, player.position(), player.getServer().getTickCount()));
        PacketDistributor.sendToPlayer(player, new SkyNet.Respawn(area, player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getServer().getTickCount()));
    }

    private static void holdPlayers(ServerTickEvent.Post event) {
        GRACE.values().removeIf(until -> until <= event.getServer().getTickCount());
        for (var entry : HOLDS.entrySet()) {
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player == null) continue;
            Vec3 pos = entry.getValue().position;
            player.setPos(pos.x, pos.y, pos.z);
            player.setDeltaMovement(Vec3.ZERO);
            player.resetFallDistance();
        }
    }

    public static boolean protectedNow(ServerPlayer player) {
        return HOLDS.containsKey(player.getUUID())
            || GRACE.getOrDefault(player.getUUID(), 0) > player.getServer().getTickCount();
    }

    public static final class SpawnData extends SavedData {
        int area;
        Vec3 position = Vec3.ZERO;
        float yaw;
        static SpawnData load(CompoundTag tag, HolderLookup.Provider registries) {
            SpawnData data = new SpawnData();
            data.area = tag.getInt("area");
            data.position = new Vec3(tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z"));
            data.yaw = tag.getFloat("yaw");
            return data;
        }
        @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            tag.putInt("area", area);
            tag.putDouble("x", position.x); tag.putDouble("y", position.y); tag.putDouble("z", position.z);
            tag.putFloat("yaw", yaw);
            return tag;
        }
    }
}
