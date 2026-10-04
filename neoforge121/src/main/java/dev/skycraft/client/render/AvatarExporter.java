package dev.skycraft.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.skycraft.SkyCraft;
import dev.skycraft.client.mixin.ParticleEngineAccessor;
import dev.skycraft.client.mixin.TextureManagerAccessor;
import dev.skycraft.combat.SkyrimActorEntity;
import dev.skycraft.link.Proto;
import dev.skycraft.link.SkyLink;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.HttpTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.opengl.GL11;

/**
 * Adapts Minecraft 1.21.1's immediate entity, block-entity and particle renderers to SkyCraft's
 * generic REN_SCENE triangle stream. The original 1.21.1 port left this adapter empty, making
 * every modded BER (Mekanism turbines, IE machines, etc.), primed TNT and particles vanish.
 */
final class AvatarExporter implements MultiBufferSource {
	private static final int SOLID = 1 | 8 | (7 << 4);
	private static final int BLENDED = 2 | 8 | (7 << 4);
	private static final int PARTICLE = 1 | 8;
	private static final int PARTICLE_BLENDED = 2 | 8;
	private static final int UV_RAW = 0, UV_BLOCK_ATLAS = 1;
	private static final double SCENE_RANGE = 64.0, BLOCK_ENTITY_RANGE = 96.0;
	private static final int MAX_ENTITIES = 64, MAX_BLOCK_ENTITIES = 128, MAX_PARTICLES = 1024;
	private static final long SCENE_INTERVAL_NANOS = 33_333_333L;
	private static final long BLOCK_ENTITY_SCAN_INTERVAL_NANOS = 1_000_000_000L;
	private static final Direction[] FACES_AND_NONE = {
		Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, null
	};
	private static final RandomSource RANDOM = RandomSource.create();

	private static final AvatarExporter SCENE = new AvatarExporter();
	private static final Map<ResourceLocation, Integer> TEXTURE_IDS = new HashMap<>();
	private static final Set<ResourceLocation> UNUSABLE_TEXTURES = new HashSet<>();
	private static int nextTextureId = 1;

	private final Map<Long, Batch> batches = new HashMap<>();
	private final Map<RenderType, Batch> renderTypeBatches = new IdentityHashMap<>();
	private final Set<String> warned = new HashSet<>();
	private final Capture capture = new Capture();
	private final List<BlockEntity> nearbyBlockEntities = new ArrayList<>();
	private SkyAtlas atlas;
	private float offX, offY, offZ;
	private boolean shown;
	private long nextSceneNanos;
	private long nextBlockEntityScanNanos;
	private int scanChunkX = Integer.MIN_VALUE, scanChunkZ = Integer.MIN_VALUE;
	private String activeRendererName;
	private Entity activeEntity;
	private int activeGetBufferCalls, activeNullBufferCalls, activeVertexCalls;
	private final List<String> activeRenderTypes = new ArrayList<>();

	private AvatarExporter() {
	}

	static void reset() {
		TEXTURE_IDS.clear();
		UNUSABLE_TEXTURES.clear();
		nextTextureId = 1;
		SCENE.renderTypeBatches.clear();
		SCENE.warned.clear();
		SCENE.shown = false;
		SCENE.nextSceneNanos = 0;
		SCENE.nextBlockEntityScanNanos = 0;
		SCENE.scanChunkX = SCENE.scanChunkZ = Integer.MIN_VALUE;
		SCENE.nearbyBlockEntities.clear();
	}

	static void frame(Minecraft minecraft, SkyAtlas atlas, float partialTick) {
		long now = System.nanoTime();
		if (now < SCENE.nextSceneNanos) return;
		SCENE.nextSceneNanos = now + SCENE_INTERVAL_NANOS;
		SCENE.exportScene(minecraft, atlas, partialTick);
	}

	private void exportScene(Minecraft minecraft, SkyAtlas atlas, float partialTick) {
		ClientLevel level = minecraft.level;
		if (level == null || minecraft.player == null) return;
		this.atlas = atlas;
		for (Batch b : this.batches.values()) b.count = 0;
		Camera camera = minecraft.gameRenderer.getMainCamera();
		Vec3 cam = camera.getPosition();
		double[] origin = {Math.floor(cam.x), Math.floor(cam.y), Math.floor(cam.z)};
		PoseStack pose = new PoseStack();

		int entityCount = 0;
		for (Entity entity : level.entitiesForRendering()) {
			if ((entity == minecraft.player && minecraft.options.getCameraType().isFirstPerson())
				|| entity instanceof ItemEntity || entity instanceof AbstractArrow
				|| entity instanceof ItemSupplier || entity instanceof SkyrimActorEntity
				|| entity.distanceToSqr(cam) > SCENE_RANGE * SCENE_RANGE || entityCount >= MAX_ENTITIES) continue;
			entityCount++;
			int verticesBefore = capturedVertices();
			try {
				this.activeEntity = entity;
				double x = Mth.lerp(partialTick, entity.xOld, entity.getX()) - origin[0];
				double y = Mth.lerp(partialTick, entity.yOld, entity.getY()) - origin[1];
				double z = Mth.lerp(partialTick, entity.zOld, entity.getZ()) - origin[2];
				int light = minecraft.getEntityRenderDispatcher().getPackedLightCoords(entity, partialTick);
				if (entity instanceof PrimedTnt tnt) {
					exportPrimedTnt(minecraft, level, tnt, x, y, z, light, partialTick, pose);
				} else {
					minecraft.getEntityRenderDispatcher().render(entity, x, y, z, entity.getYRot(), partialTick, pose, this, light);
				}
				this.capture.flush();
				if (entity instanceof PrimedTnt && capturedVertices() == verticesBefore) {
					warnOnce("empty-primed-tnt", "primed TNT renderer emitted no compatible geometry", null);
				}
			} catch (Throwable ex) {
				warnOnce("entity:" + entity.getType(), "couldn't capture entity " + entity.getType() + " for Skyrim", ex);
			} finally {
				this.activeEntity = null;
			}
		}

		exportBlockEntities(minecraft, level, cam, origin, partialTick, pose);
		exportParticles(minecraft, camera, cam, origin, partialTick);
		send(origin);
	}

	/** Vanilla's TNT renderer goes through a GPU-oriented block layer that is not capturable here. */
	private void exportPrimedTnt(Minecraft minecraft, ClientLevel level, PrimedTnt tnt, double x, double y, double z,
		int light, float partialTick, PoseStack pose) {
		pose.pushPose();
		try {
			pose.translate(x, y + 0.5, z);
			int fuse = tnt.getFuse();
			if ((float) fuse - partialTick + 1.0F < 10.0F) {
				float pulse = 1.0F - ((float) fuse - partialTick + 1.0F) / 10.0F;
				pulse = Mth.clamp(pulse, 0.0F, 1.0F);
				pulse *= pulse;
				pulse *= pulse;
				float scale = 1.0F + pulse * 0.3F;
				pose.scale(scale, scale, scale);
			}
			pose.mulPose(Axis.YP.rotationDegrees(-90.0F));
			pose.translate(-0.5, -0.5, 0.5);
			pose.mulPose(Axis.YP.rotationDegrees(90.0F));

			var state = tnt.getBlockState();
			var model = minecraft.getBlockRenderer().getBlockModel(state);
			BlockPos pos = tnt.blockPosition();
			var modelData = model.getModelData(level, pos, state, level.getModelData(pos));
			RANDOM.setSeed(state.getSeed(pos));
			this.capture.begin(batch(0, UV_BLOCK_ATLAS, SOLID));
			this.capture.flash = fuse / 5 % 2 == 0;
			for (Direction face : FACES_AND_NONE) {
				for (var quad : model.getQuads(state, face, RANDOM, modelData, null)) {
					this.capture.putBulkData(pose.last(), quad, 1.0F, 1.0F, 1.0F, 1.0F, light,
						OverlayTexture.NO_OVERLAY, true);
				}
			}
			this.capture.flush();
		} finally {
			this.capture.flash = false;
			pose.popPose();
		}
	}

	private void exportBlockEntities(Minecraft minecraft, ClientLevel level, Vec3 cam, double[] origin, float partialTick, PoseStack pose) {
		var dispatcher = minecraft.getBlockEntityRenderDispatcher();
		dispatcher.prepare(level, minecraft.gameRenderer.getMainCamera(), minecraft.hitResult);
		int cameraChunkX = Mth.floor(cam.x / 16.0), cameraChunkZ = Mth.floor(cam.z / 16.0);
		long now = System.nanoTime();
		if (now >= nextBlockEntityScanNanos || cameraChunkX != scanChunkX || cameraChunkZ != scanChunkZ) {
			nearbyBlockEntities.clear();
			int minX = Mth.floor((cam.x - BLOCK_ENTITY_RANGE) / 16.0), maxX = Mth.floor((cam.x + BLOCK_ENTITY_RANGE) / 16.0);
			int minZ = Mth.floor((cam.z - BLOCK_ENTITY_RANGE) / 16.0), maxZ = Mth.floor((cam.z + BLOCK_ENTITY_RANGE) / 16.0);
			for (int cx = minX; cx <= maxX && nearbyBlockEntities.size() < MAX_BLOCK_ENTITIES; cx++) {
				for (int cz = minZ; cz <= maxZ && nearbyBlockEntities.size() < MAX_BLOCK_ENTITIES; cz++) {
					var chunk = level.getChunkSource().getChunk(cx, cz, false);
					if (chunk == null) continue;
					for (var blockEntity : chunk.getBlockEntities().values()) {
						if (!blockEntity.isRemoved() && blockEntity.getBlockPos().distToCenterSqr(cam) <= BLOCK_ENTITY_RANGE * BLOCK_ENTITY_RANGE
							&& dispatcher.getRenderer(blockEntity) != null) nearbyBlockEntities.add(blockEntity);
						if (nearbyBlockEntities.size() >= MAX_BLOCK_ENTITIES) break;
					}
				}
			}
			nextBlockEntityScanNanos = now + BLOCK_ENTITY_SCAN_INTERVAL_NANOS;
			scanChunkX = cameraChunkX;
			scanChunkZ = cameraChunkZ;
		}
		int count = 0;
		for (var blockEntity : nearbyBlockEntities) {
					BlockPos pos = blockEntity.getBlockPos();
					if (blockEntity.isRemoved() || pos.distToCenterSqr(cam) > BLOCK_ENTITY_RANGE * BLOCK_ENTITY_RANGE) continue;
					count++;
					int verticesBefore = capturedVertices();
					var renderer = dispatcher.getRenderer(blockEntity);
					String rendererClass = renderer == null ? "none" : renderer.getClass().getName();
					String rendererName = rendererClass.toLowerCase();
					boolean auditRenderer = rendererName.contains("windgenerator") || rendererName.contains("schematicannon");
					activeRendererName = auditRenderer ? rendererClass : null;
					activeGetBufferCalls = activeNullBufferCalls = activeVertexCalls = 0;
					activeRenderTypes.clear();
					pose.pushPose();
					try {
						pose.translate(pos.getX() - origin[0], pos.getY() - origin[1], pos.getZ() - origin[2]);
						dispatcher.render(blockEntity, partialTick, pose, this);
						this.capture.flush();
						int emitted = capturedVertices() - verticesBefore;
						if (auditRenderer && emitted == 0) {
							warnOnce("empty-target:" + rendererClass, rendererClass + " emitted no compatible geometry"
								+ " (getBuffer=" + activeGetBufferCalls + ", nullBuffer=" + activeNullBufferCalls
								+ ", addVertex=" + activeVertexCalls + ", renderTypes=" + activeRenderTypes + ")", null);
						} else if (auditRenderer) {
							warnOnce("captured-target:" + rendererClass, "captured " + emitted + " vertices from " + rendererClass
								+ " (getBuffer=" + activeGetBufferCalls + ", renderTypes=" + activeRenderTypes + ")", null);
						}
					} catch (Throwable ex) {
						warnOnce("blockentity:" + blockEntity.getType(), "couldn't capture block entity " + blockEntity.getType() + " for Skyrim", ex);
					} finally {
						activeRendererName = null;
						pose.popPose();
					}
		}
	}

	private int capturedVertices() {
		int total = this.capture.pending ? 1 : 0;
		for (Batch batch : this.batches.values()) total += batch.count;
		return total;
	}

	private void exportParticles(Minecraft minecraft, Camera camera, Vec3 cam, double[] origin, float partialTick) {
		Map<ParticleRenderType, Queue<Particle>> groups = ((ParticleEngineAccessor) minecraft.particleEngine).skycraft$particles();
		int count = 0;
		this.offX = (float) (cam.x - origin[0]);
		this.offY = (float) (cam.y - origin[1]);
		this.offZ = (float) (cam.z - origin[2]);
		for (var entry : groups.entrySet()) {
			ParticleRenderType type = entry.getKey();
			Batch batch = particleBatch(type);
			if (batch == null) continue;
			this.capture.begin(batch);
			for (Particle particle : entry.getValue()) {
				if (++count > MAX_PARTICLES) break;
				try {
					particle.render(this.capture, camera, partialTick);
				} catch (Throwable ex) {
					warnOnce("particle:" + type, "couldn't capture particle group " + type + " for Skyrim", ex);
					break;
				}
			}
			this.capture.flush();
			if (count > MAX_PARTICLES) break;
		}
		this.offX = this.offY = this.offZ = 0;
	}

	@Nullable
	private Batch particleBatch(ParticleRenderType type) {
		if (type == ParticleRenderType.NO_RENDER || type == ParticleRenderType.CUSTOM) {
			if (type == ParticleRenderType.CUSTOM) warnOnce("particle-custom", "custom GPU particles remain unsupported", null);
			return null;
		}
		if (type == ParticleRenderType.TERRAIN_SHEET) return batch(0, UV_BLOCK_ATLAS, PARTICLE_BLENDED);
		int texture = textureId(TextureAtlas.LOCATION_PARTICLES);
		return texture < 0 ? null : batch(texture, UV_RAW, type.isTranslucent() ? PARTICLE_BLENDED : PARTICLE);
	}

	@Override
	public VertexConsumer getBuffer(RenderType renderType) {
		if (this.activeRendererName != null) {
			this.activeGetBufferCalls++;
			String description = renderType.toString();
			if (this.activeRenderTypes.size() < 8 && !this.activeRenderTypes.contains(description)) this.activeRenderTypes.add(description);
		}
		// PlayerModel uses a translucent layer for its transparent outer skin. Drawing the entire
		// body as blended skips depth writes, letting the rear of the head and torso show through the
		// front. Export player pixels as alpha-cutout instead: opaque skin writes depth and genuinely
		// transparent overlay pixels are discarded.
		boolean playerCutout = this.activeEntity instanceof Player;
		Batch b = playerCutout ? null : this.renderTypeBatches.get(renderType);
		if (b == null && (playerCutout || !this.renderTypeBatches.containsKey(renderType))) {
			b = makeBatch(renderType, playerCutout);
			if (!playerCutout) this.renderTypeBatches.put(renderType, b);
		}
		if (b == null) {
			if (this.activeRendererName != null) this.activeNullBufferCalls++;
			return NullVertexConsumer.INSTANCE;
		}
		this.capture.begin(b);
		return this.capture;
	}

	@Nullable
	private Batch makeBatch(RenderType renderType, boolean forceCutout) {
		String name = renderType.toString().toLowerCase();
		// Every CompositeState description contains a line_width state, so searching the whole
		// string for "line" rejects normal solid/entity layers as well as actual line geometry.
		int open = name.indexOf('['), colon = name.indexOf(':', open + 1);
		String layerName = open >= 0 ? name.substring(open + 1, colon > open ? colon : name.length()) : name;
		if (layerName.contains("glint") || layerName.contains("outline") || layerName.contains("shadow")
			|| layerName.equals("lines") || layerName.equals("line_strip")) return null;
		ResourceLocation texture = textureOf(renderType);
		if (texture == null) {
			// Chunk-style block layers bind the block atlas outside their RenderType state. TNT and
			// several modded moving-block renderers use these layers from an entity renderer.
			if (renderType == RenderType.solid() || renderType == RenderType.cutout()
				|| renderType == RenderType.cutoutMipped() || renderType == RenderType.translucent()) {
				int flags = !forceCutout && renderType == RenderType.translucent() ? BLENDED : SOLID;
				return batch(0, UV_BLOCK_ATLAS, flags);
			}
			warnOnce("rendertype:" + renderType, "unsupported untextured render type " + renderType, null);
			return null;
		}
		int flags = !forceCutout && (renderType.sortOnUpload() || name.contains("translucent")) ? BLENDED : SOLID;
		if (texture.equals(TextureAtlas.LOCATION_BLOCKS)) return batch(0, UV_BLOCK_ATLAS, flags);
		int id = textureId(texture);
		return id < 0 ? null : batch(id, UV_RAW, flags);
	}

	@SuppressWarnings("unchecked")
	private ResourceLocation textureOf(RenderType renderType) {
		try {
			Field state = renderType.getClass().getDeclaredField("state");
			state.setAccessible(true);
			Object composite = state.get(renderType);
			Field textureState = composite.getClass().getDeclaredField("textureState");
			textureState.setAccessible(true);
			Object shard = textureState.get(composite);
			Method cutout = shard.getClass().getDeclaredMethod("cutoutTexture");
			cutout.setAccessible(true);
			return ((Optional<ResourceLocation>) cutout.invoke(shard)).orElse(null);
		} catch (ReflectiveOperationException ex) {
			warnOnce("reflection:" + renderType.getClass(), "couldn't inspect render type " + renderType, ex);
			return null;
		}
	}

	private Batch batch(int texture, int uvMode, int flags) {
		long key = ((long) texture << 16) | ((long) uvMode << 8) | flags;
		return this.batches.computeIfAbsent(key, k -> new Batch(texture, uvMode, flags));
	}

	private void send(double[] origin) {
		this.capture.flush();
		List<Batch> used = new ArrayList<>();
		int vertices = 0;
		for (Batch b : this.batches.values()) {
			if (b.count >= 4) {
				used.add(b);
				vertices += b.count / 4 * 6;
			}
		}
		if (used.isEmpty()) {
			if (shown) {
				ByteBuffer h = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN);
				h.putDouble(origin[0]).putDouble(origin[1]).putDouble(origin[2]).putInt(0).putInt(0).flip();
				shown = !SkyLink.writeRender(Proto.REN_SCENE, h, null);
			}
			return;
		}
		ByteBuffer header = ByteBuffer.allocate(32 + used.size() * 16).order(ByteOrder.LITTLE_ENDIAN);
		header.putDouble(origin[0]).putDouble(origin[1]).putDouble(origin[2]).putInt(used.size()).putInt(vertices);
		ByteBuffer body = ByteBuffer.allocateDirect(vertices * Proto.REN_VERTEX_BYTES).order(ByteOrder.LITTLE_ENDIAN);
		int first = 0;
		for (Batch b : used) {
			int n = b.count / 4 * 6;
			header.putInt(b.texture).putInt(first).putInt(n).putInt((b.flags & 2) != 0 ? 1 : 0);
			b.write(body);
			first += n;
		}
		header.flip();
		body.flip();
		if (SkyLink.tryWriteRender(Proto.REN_SCENE, header, body)) shown = true;
	}

	private final class Batch {
		final int texture, uvMode, flags;
		int[] data = new int[8 * 256];
		int count;
		Batch(int texture, int uvMode, int flags) { this.texture = texture; this.uvMode = uvMode; this.flags = flags; }
		void add(float x, float y, float z, float u, float v, int color, int light, int overlay) {
			if ((count + 1) * 8 > data.length) data = java.util.Arrays.copyOf(data, data.length * 2);
			if (uvMode == UV_BLOCK_ATLAS) { u = atlas.blockU(u); v = atlas.blockV(v); }
			if (((overlay >>> 16) & 0xffff) < 8) {
				int r = color >> 16 & 255, g = (int) ((color >> 8 & 255) * .55f), b = (int) ((color & 255) * .55f);
				color = color & 0xff000000 | r << 16 | g << 8 | b;
			}
			if ((overlay & 0xffff) >= 8) light = LightTexture.FULL_BRIGHT;
			int o = count++ * 8;
			data[o] = Float.floatToRawIntBits(x + offX); data[o + 1] = Float.floatToRawIntBits(y + offY); data[o + 2] = Float.floatToRawIntBits(z + offZ);
			data[o + 3] = Float.floatToRawIntBits(u); data[o + 4] = Float.floatToRawIntBits(v); data[o + 5] = color;
			data[o + 6] = (light >> 4 & 15) | (light >> 20 & 15) << 8; data[o + 7] = flags;
		}
		void write(ByteBuffer out) {
			for (int q = 0; q + 4 <= count; q += 4) for (int k : new int[]{0, 1, 2, 0, 2, 3}) {
				int o = (q + k) * 8, c = data[o + 5];
				out.putInt(data[o]).putInt(data[o + 1]).putInt(data[o + 2]).putInt(data[o + 3]).putInt(data[o + 4]);
				out.put((byte) (c >> 16)).put((byte) (c >> 8)).put((byte) c).put((byte) (c >>> 24));
				out.putInt(data[o + 6]).putInt(data[o + 7]);
			}
		}
	}

	private final class Capture implements VertexConsumer {
		Batch batch; boolean pending, flash; float x, y, z, u, v; int color, light, overlay;
		void begin(Batch b) { flush(); batch = b; }
		void flush() {
			if (pending && batch != null) {
				if (flash) { color = 0xFFFFB0B0; light = LightTexture.FULL_BRIGHT; }
				batch.add(x, y, z, u, v, color, light, overlay);
			}
			pending = false;
		}
		@Override public VertexConsumer addVertex(float x, float y, float z) { flush(); if (activeRendererName != null) activeVertexCalls++; this.x=x;this.y=y;this.z=z;u=v=0;color=-1;light=LightTexture.FULL_BRIGHT;overlay=OverlayTexture.NO_OVERLAY;pending=true;return this; }
		@Override public VertexConsumer setColor(int r, int g, int b, int a) { color=a<<24|r<<16|g<<8|b;return this; }
		@Override public VertexConsumer setUv(float u, float v) { this.u=u;this.v=v;return this; }
		@Override public VertexConsumer setUv1(int u, int v) { overlay=u&0xffff|v<<16;return this; }
		@Override public VertexConsumer setUv2(int u, int v) { light=u&0xffff|v<<16;return this; }
		@Override public VertexConsumer setNormal(float x, float y, float z) { return this; }
	}

	private enum NullVertexConsumer implements VertexConsumer {
		INSTANCE;
		@Override public VertexConsumer addVertex(float x,float y,float z){return this;}
		@Override public VertexConsumer setColor(int r,int g,int b,int a){return this;}
		@Override public VertexConsumer setUv(float u,float v){return this;}
		@Override public VertexConsumer setUv1(int u,int v){return this;}
		@Override public VertexConsumer setUv2(int u,int v){return this;}
		@Override public VertexConsumer setNormal(float x,float y,float z){return this;}
	}

	private static int textureId(ResourceLocation texture) {
		Integer old = TEXTURE_IDS.get(texture);
		if (old != null) return old;
		if (UNUSABLE_TEXTURES.contains(texture)) return -1;
		NativeImage image = readTexture(texture);
		if (image == null) {
			// Downloaded player skins are registered before their HTTP upload is necessarily complete.
			// Retry those instead of remembering a transient miss forever (which produced floating armor).
			AbstractTexture registered = ((TextureManagerAccessor) Minecraft.getInstance().getTextureManager()).skycraft$byPath().get(texture);
			if (!(registered instanceof HttpTexture)) UNUSABLE_TEXTURES.add(texture);
			SkyCraft.LOG.info("SkyCraft compatibility audit: unavailable texture {}", texture);
			return -1;
		}
		int id = nextTextureId++;
		try (image) {
			int w = image.getWidth(), h = image.getHeight();
			ByteBuffer pixels = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.LITTLE_ENDIAN);
			for (int y=0;y<h;y++) for (int x=0;x<w;x++) { int abgr=image.getPixelRGBA(x,y); pixels.put((byte)abgr).put((byte)(abgr>>8)).put((byte)(abgr>>16)).put((byte)(abgr>>>24)); }
			pixels.flip();
			ByteBuffer header = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN).putInt(id).putInt(w).putInt(h).putInt(0).flip();
			if (!SkyLink.writeRender(Proto.REN_TEXTURE, header, pixels)) { nextTextureId--; return -1; }
		}
		TEXTURE_IDS.put(texture, id);
		return id;
	}

	@Nullable
	private static NativeImage readTexture(ResourceLocation texture) {
		Minecraft mc = Minecraft.getInstance();
		var resource = mc.getResourceManager().getResource(texture);
		if (resource.isPresent()) try (var in = resource.get().open()) { return NativeImage.read(in); } catch (Exception ex) { SkyCraft.LOG.warn("SkyCraft: couldn't read {}", texture, ex); }
		AbstractTexture registered = ((TextureManagerAccessor) mc.getTextureManager()).skycraft$byPath().get(texture);
		if (registered instanceof DynamicTexture dynamic && dynamic.getPixels() != null) { NativeImage copy=new NativeImage(dynamic.getPixels().getWidth(),dynamic.getPixels().getHeight(),false);copy.copyFrom(dynamic.getPixels());return copy; }
		if (registered instanceof TextureAtlas atlas) return SkyAtlas.image(atlas);
		// Player skins (HttpTexture) and some mod textures only retain their uploaded GL image. Read
		// that image back while we are on the render thread so their body/model is exported too.
		if (registered != null) {
			int previous = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
			try {
				GL11.glBindTexture(GL11.GL_TEXTURE_2D, registered.getId());
				int width = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
				int height = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
				if (width > 0 && height > 0 && width <= 4096 && height <= 4096) {
					NativeImage copy = new NativeImage(width, height, false);
					copy.downloadTexture(0, false);
					return copy;
				}
			} catch (RuntimeException ex) {
				SkyCraft.LOG.debug("SkyCraft: GPU texture readback failed for {}", texture, ex);
			} finally {
				GL11.glBindTexture(GL11.GL_TEXTURE_2D, previous);
			}
		}
		return null;
	}

	private void warnOnce(String key, String message, @Nullable Throwable ex) {
		if (!warned.add(key)) return;
		if (ex == null) SkyCraft.LOG.info("SkyCraft compatibility audit: {}", message);
		else SkyCraft.LOG.warn("SkyCraft compatibility audit: {}", message, ex);
	}
}
