package dev.skycraft.world;

import dev.skycraft.net.SkyNet;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.phys.Vec3;

/** Standalone regression checks; not included in the production mod or installer. */
public final class RespawnStateTest {
    public static void main(String[] args) throws Exception {
        SkyRespawn.SpawnData saved = new SkyRespawn.SpawnData();
        saved.area = 0x0000003c;
        saved.position = new Vec3(-1035.125, 337.75, 513.5);
        saved.yaw = 275.25f;
        SkyRespawn.SpawnData loaded = SkyRespawn.SpawnData.load(saved.save(new CompoundTag(), null), null);
        check(loaded.area == saved.area && loaded.position.equals(saved.position) && loaded.yaw == saved.yaw, "spawn persistence");
        check(SkyRespawn.SpawnData.load(new CompoundTag(), null).area == 0, "fresh world has no checkpoint");
        check(SkyRespawn.supportedSurface(337.503, 337.5), "streamed terrain supports feet without onGround flag");
        check(!SkyRespawn.supportedSurface(339, 337.5), "airborne player rejected");
        check(!SkyRespawn.supportedSurface(337.5, Double.NaN), "unknown terrain rejected");
        var bedPos = new net.minecraft.core.BlockPos(100, 200, -300);
        var bed = new CompoundTag();
        bed.putInt("area", 0x1234);
        bed.putLong("pos", bedPos.asLong());
        bed.putString("dimension", "minecraft:overworld");
        check(SkyRespawn.hasValidBed(bed, bedPos, net.minecraft.world.level.Level.OVERWORLD, false), "valid bed selected");
        check(!SkyRespawn.hasValidBed(bed, bedPos, net.minecraft.world.level.Level.OVERWORLD, true), "missing/blocked bed falls back");
        check(!SkyRespawn.hasValidBed(bed, null, net.minecraft.world.level.Level.OVERWORLD, false), "no bed falls back");
        check(!SkyRespawn.hasValidBed(bed, bedPos.above(), net.minecraft.world.level.Level.OVERWORLD, false), "stale bed position rejected");
        check(!SkyRespawn.hasValidBed(bed, bedPos, net.minecraft.world.level.Level.NETHER, false), "wrong dimension rejected");
        var packet = new SkyNet.Respawn(0x0000003c, -1035.125, 337.75, 513.5, 275.25f, 12345);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            SkyNet.Respawn.CODEC.encode(buffer, packet);
            check(SkyNet.Respawn.CODEC.decode(buffer).equals(packet), "respawn packet round trip");
            buffer.clear();
            var area = new SkyNet.AreaSync(0x12345678);
            SkyNet.AreaSync.CODEC.encode(buffer, area);
            check(SkyNet.AreaSync.CODEC.decode(buffer).equals(area), "area packet round trip");
            buffer.clear();
            var ready = new SkyNet.RespawnReady(12345);
            SkyNet.RespawnReady.CODEC.encode(buffer, ready);
            check(SkyNet.RespawnReady.CODEC.decode(buffer).equals(ready), "readiness token round trip");
        } finally { buffer.release(); }
        if (args.length > 0) {
            var level = net.minecraft.nbt.NbtIo.readCompressed(java.nio.file.Path.of(args[0]), net.minecraft.nbt.NbtAccounter.unlimitedHeap());
            var rules = level.getCompound("Data").getCompound("GameRules");
            check(rules.getString("keepInventory").equals("false"), "server configured inventory death rules");
            check(rules.getString("doImmediateRespawn").equals("false"), "server configured respawn button");
            System.out.println("PASS: isolated server saved keepInventory=false, doImmediateRespawn=false");
        }
        System.out.println("PASS: persistence, three terrain support checks, five bed/fallback cases, three packet round trips");
    }
    private static void check(boolean passed, String name) {
        if (!passed) throw new AssertionError(name);
    }
}
