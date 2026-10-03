package dev.skycraft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.skycraft.world.SkyWater;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Makes Skyrim water participate in 1.21.1 entity swimming, currents and eye submersion. */
@Mixin(Entity.class)
public abstract class EntityFluidInteractionMixin {
	@WrapOperation(
		method = { "updateFluidHeightAndDoFluidPushing()V", "updateFluidOnEyes()V" },
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getFluidState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/material/FluidState;")
	)
	private FluidState skycraft$skyrimWater(Level level, BlockPos pos, Operation<FluidState> original) {
		FluidState state = original.call(level, pos);
		if (state.isEmpty() && SkyWater.active()) {
			FluidState water = SkyWater.fluidAt(level, pos);
			if (water != null) {
				return water;
			}
		}
		return state;
	}

	@WrapOperation(
		method = "updateFluidHeightAndDoFluidPushing()V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/material/FluidState;getHeight(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)F")
	)
	private float skycraft$skyrimWaterHeight(FluidState state, BlockGetter level, BlockPos pos, Operation<Float> original) {
		float height = SkyWater.active() ? SkyWater.substitutedHeight(level, pos) : -1.0F;
		return height >= 0.0F ? height : original.call(state, level, pos);
	}

	@WrapOperation(
		method = "updateFluidOnEyes()V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/material/FluidState;getHeight(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)F")
	)
	private float skycraft$skyrimWaterEyeHeight(FluidState state, BlockGetter level, BlockPos pos, Operation<Float> original) {
		float height = SkyWater.active() ? SkyWater.substitutedHeight(level, pos) : -1.0F;
		return height >= 0.0F ? height : original.call(state, level, pos);
	}
}
