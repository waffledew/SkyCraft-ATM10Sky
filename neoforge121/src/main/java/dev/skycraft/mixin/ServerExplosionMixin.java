package dev.skycraft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.skycraft.link.Proto;
import dev.skycraft.link.SkyLink;
import dev.skycraft.world.SkyDigBlast;
import java.util.List;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Minecraft explosions in Skyrim's world: they blow Skyrim's ground and rock apart like blocks
 * (SkyDigBlast), and Skyrim feels them (loose objects are thrown and people knocked away).
 */
@Mixin(Explosion.class)
public abstract class ServerExplosionMixin {
	@Shadow @Final private Level level;

	@Unique
	private @Nullable SkyDigBlast skycraft$blast;

	@Inject(method = "explode", at = @At("HEAD"))
	private void skycraft$begin(CallbackInfo ci) {
		if (this.level instanceof ServerLevel serverLevel) {
			this.skycraft$blast = SkyDigBlast.begin((Explosion) (Object) this, serverLevel);
		}
	}

	@WrapOperation(
		method = "explode",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/level/ExplosionDamageCalculator;getBlockExplosionResistance(Lnet/minecraft/world/level/Explosion;Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/material/FluidState;)Ljava/util/Optional;"
		)
	)
	private Optional<Float> skycraft$skyrimResists(
		ExplosionDamageCalculator calculator, Explosion explosion, BlockGetter level, BlockPos pos, BlockState block, FluidState fluid, Operation<Optional<Float>> original
	) {
		Optional<Float> vanilla = original.call(calculator, explosion, level, pos, block, fluid);
		return this.skycraft$blast != null ? this.skycraft$blast.resistance(pos, vanilla) : vanilla;
	}

	@WrapOperation(
		method = "explode",
		at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/objects/ObjectArrayList;addAll(Ljava/util/Collection;)Z")
	)
	private boolean skycraft$skyrimBreaks(ObjectArrayList<BlockPos> destination, Collection<? extends BlockPos> targets, Operation<Boolean> original) {
		Collection<? extends BlockPos> materialized = targets;
		if (this.skycraft$blast != null) {
			Explosion self = (Explosion) (Object) this;
			List<BlockPos> copy = new ArrayList<>(targets);
			this.skycraft$blast.materialize(copy, self.interactsWithBlocks());
			materialized = copy;
		}
		return original.call(destination, materialized);
	}

	@Inject(method = "explode", at = @At("RETURN"))
	private void skycraft$tellSkyrim(CallbackInfo ci) {
		if (this.skycraft$blast != null) {
			this.skycraft$blast.finish();
			this.skycraft$blast = null;
		}
		if (!SkyLink.active()) {
			return;
		}
		Explosion self = (Explosion) (Object) this;
		var center = self.center();
		SkyLink.pushEvent(Proto.EV_EXPLOSION, 0, (float) center.x, (float) center.y, (float) center.z, self.radius(), 0);
	}
}
