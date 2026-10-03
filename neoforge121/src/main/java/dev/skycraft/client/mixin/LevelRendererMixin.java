package dev.skycraft.client.mixin;

import dev.skycraft.client.SkyClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Skyrim draws the world. While linked, Minecraft renders nothing of its own level (no sky,
 * clouds, fog or terrain) so the overlay is just hand + HUD on a transparent background.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
	@Inject(
		method = "renderLevel",
		at = @At("HEAD"),
		cancellable = true
	)
	private void skycraft$skipLevel(CallbackInfo ci) {
		if (SkyClient.linked()) {
			// Vanilla prepares the entity dispatcher at the start of renderLevel. We cancel the
			// rest of that method because Skyrim draws the world, but GUI screens rendered later
			// in the same frame (notably the inventory) still use the dispatcher for their player
			// preview. Leaving it unprepared gives ATM10's name-tag mixins a null camera.
			Minecraft minecraft = Minecraft.getInstance();
			minecraft.getEntityRenderDispatcher().prepare(
				minecraft.level,
				minecraft.gameRenderer.getMainCamera(),
				minecraft.crosshairPickEntity
			);
			ci.cancel();
		}
	}
}
