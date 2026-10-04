package dev.skycraft.world;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Conservative natural spawning for the otherwise-empty mirror dimension. */
public final class SkyMobSpawner {
	private static final List<EntityType<?>> MONSTERS = List.of(EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.CREEPER);
	private static final List<EntityType<?>> CREATURES = List.of(EntityType.COW, EntityType.SHEEP, EntityType.PIG, EntityType.CHICKEN);

	private SkyMobSpawner() {}

	public static void init() { NeoForge.EVENT_BUS.addListener(SkyMobSpawner::tick); }

	private static void tick(ServerTickEvent.Post event) {
		int tick = event.getServer().getTickCount();
		if (tick % 40 != 0) return;
		for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
			if (player.isSpectator()) continue;
			ServerLevel level = player.serverLevel();
			if (!level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)) continue;
			AABB nearby = player.getBoundingBox().inflate(40.0, 24.0, 40.0);
			long monsters = level.getEntitiesOfClass(Mob.class, nearby, mob -> mob.getType().getCategory() == MobCategory.MONSTER).size();
			if (level.getDifficulty() != Difficulty.PEACEFUL && monsters < 12) spawnNear(level, player, MONSTERS);
			if (tick % 200 == 0) {
				long creatures = level.getEntitiesOfClass(Mob.class, nearby, mob -> mob.getType().getCategory() == MobCategory.CREATURE).size();
				if (creatures < 6) spawnNear(level, player, CREATURES);
			}
		}
	}

	private static void spawnNear(ServerLevel level, ServerPlayer player, List<EntityType<?>> pool) {
		for (int attempt=0; attempt<8; attempt++) {
			double angle=level.random.nextDouble()*Math.PI*2.0, distance=16.0+level.random.nextDouble()*8.0;
			int x=(int)Math.floor(player.getX()+Math.cos(angle)*distance), z=(int)Math.floor(player.getZ()+Math.sin(angle)*distance);
			double y=SkyCollision.surfaceY(x,z,(int)Math.floor(player.getY())-20,(int)Math.floor(player.getY())+12);
			if (Double.isNaN(y)) continue;
			var entity=pool.get(level.random.nextInt(pool.size())).create(level);
			if (!(entity instanceof Mob mob)) continue;
			mob.moveTo(x+0.5,y+0.02,z+0.5,level.random.nextFloat()*360.0F,0.0F);
			if (!level.noCollision(mob) || !mob.checkSpawnObstruction(level)) { mob.discard(); continue; }
			mob.finalizeSpawn(level,level.getCurrentDifficultyAt(BlockPos.containing(x,y,z)),MobSpawnType.NATURAL,null);
			level.addFreshEntityWithPassengers(mob);
			return;
		}
	}
}
