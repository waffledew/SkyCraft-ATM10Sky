package dev.skycraft.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.skycraft.SkyCraft;
import dev.skycraft.link.Proto;
import dev.skycraft.link.SkyLink;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;

/** Copies Minecraft's transparent hand/HUD target into SkyCraft's shared overlay buffer. */
public final class FrameExporter {
	private static final int STAGING = 3;
	private static final Slot[] slots = new Slot[STAGING];
	private static long nextFrameId = 1;
	private static boolean logged;

	private static final class Slot {
		int pbo;
		int width;
		int height;
		long fence;
		long frameId;
	}

	private FrameExporter() {
	}

	public static void capture(Minecraft minecraft) {
		RenderSystem.assertOnRenderThread();
		shipReadyFrame();

		MemorySegment shm = SkyLink.segment();
		if (shm == null) {
			return;
		}
		RenderTarget target = minecraft.getMainRenderTarget();
		int width = target.width;
		int height = target.height;
		if (width <= 0 || height <= 0 || width > Proto.MAX_OVERLAY_W || height > Proto.MAX_OVERLAY_H) {
			return;
		}

		Slot slot = null;
		for (int i = 0; i < STAGING; i++) {
			if (slots[i] == null) {
				slots[i] = new Slot();
			}
			if (slots[i].fence == 0 && slot == null) {
				slot = slots[i];
			}
		}
		if (slot == null) {
			return; // The GPU is still using every staging buffer; skip instead of stalling Minecraft.
		}

		long bytes = (long) width * height * 4L;
		if (slot.pbo == 0) {
			slot.pbo = GL15.glGenBuffers();
		}
		GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, slot.pbo);
		if (slot.width != width || slot.height != height) {
			GL15.glBufferData(GL21.GL_PIXEL_PACK_BUFFER, bytes, GL15.GL_STREAM_READ);
			slot.width = width;
			slot.height = height;
		}
		target.bindRead();
		GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
		GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, 0L);
		slot.fence = GL32.glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
		slot.frameId = nextFrameId++;
		target.unbindRead();
		GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
		if (!logged) {
			logged = true;
			SkyCraft.LOG.info("SkyCraft: 1.21.1 overlay capture {}x{} via asynchronous OpenGL staging", width, height);
		}
	}

	/** Publishes the newest completed GPU readback without ever waiting for an unfinished one. */
	private static void shipReadyFrame() {
		Slot newest = null;
		for (Slot slot : slots) {
			if (slot == null || slot.fence == 0) {
				continue;
			}
			int status = GL32.glClientWaitSync(slot.fence, 0, 0L);
			if ((status == GL32.GL_ALREADY_SIGNALED || status == GL32.GL_CONDITION_SATISFIED)
				&& (newest == null || slot.frameId > newest.frameId)) {
				newest = slot;
			}
		}
		if (newest == null) {
			return;
		}

		MemorySegment shm = SkyLink.segment();
		long bytes = (long) newest.width * newest.height * 4L;
		GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, newest.pbo);
		ByteBuffer source = GL30.glMapBufferRange(GL21.GL_PIXEL_PACK_BUFFER, 0L, bytes, GL30.GL_MAP_READ_BIT);
		if (source != null) {
			if (shm != null) {
				MemorySegment.copy(MemorySegment.ofBuffer(source), 0, shm, SkyLink.overlayBackSlotOffset(), bytes);
				SkyLink.publishOverlay(newest.width, newest.height, true, newest.frameId);
			}
			GL15.glUnmapBuffer(GL21.GL_PIXEL_PACK_BUFFER);
		}
		GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);

		// Completed frames older than the one just published are no longer useful.
		for (Slot slot : slots) {
			if (slot != null && slot.fence != 0 && slot.frameId <= newest.frameId) {
				int status = GL32.glClientWaitSync(slot.fence, 0, 0L);
				if (status == GL32.GL_ALREADY_SIGNALED || status == GL32.GL_CONDITION_SATISFIED) {
					GL32.glDeleteSync(slot.fence);
					slot.fence = 0;
				}
			}
		}
	}
}
