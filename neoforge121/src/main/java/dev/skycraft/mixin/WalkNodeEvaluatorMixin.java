package dev.skycraft.mixin;

import dev.skycraft.world.SkyCollision;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Makes mob AI regard an air cell resting on Skyrim terrain as walkable ground. */
@Mixin(WalkNodeEvaluator.class)
public abstract class WalkNodeEvaluatorMixin {
	@Inject(method = "getPathTypeStatic(Lnet/minecraft/world/level/pathfinder/PathfindingContext;Lnet/minecraft/core/BlockPos$MutableBlockPos;)Lnet/minecraft/world/level/pathfinder/PathType;", at = @At("HEAD"), cancellable = true)
	private static void skycraft$walkOnSkyrim(PathfindingContext context, BlockPos.MutableBlockPos pos, CallbackInfoReturnable<PathType> cir) {
		if (context.level().getBlockState(pos).isAir() && SkyCollision.supportsFromBelow(pos)) cir.setReturnValue(PathType.WALKABLE);
	}
}
