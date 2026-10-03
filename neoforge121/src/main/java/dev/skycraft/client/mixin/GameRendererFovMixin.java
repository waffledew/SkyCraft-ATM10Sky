package dev.skycraft.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.skycraft.client.SkyClient;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Captures the final world FOV after zoom mods such as Zume have modified GameRenderer.getFov. */
@Mixin(value = GameRenderer.class, priority = 100)
public abstract class GameRendererFovMixin {
	@ModifyReturnValue(method = "getFov", at = @At("RETURN"))
	private double skycraft$captureEffectiveFov(double fov, Camera camera, float partialTick, boolean useFovSetting) {
		if (useFovSetting) {
			SkyClient.setEffectiveFov((float) fov);
		}
		return fov;
	}
}
