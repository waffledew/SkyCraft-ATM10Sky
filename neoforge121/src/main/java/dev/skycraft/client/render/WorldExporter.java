package dev.skycraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.skycraft.SkyCraft;
import dev.skycraft.link.Proto;
import dev.skycraft.link.SkyLink;
import dev.skycraft.world.SkyClip;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.SpectralArrow;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.BlockDestructionProgress;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Ships what Minecraft would draw in the world to Skyrim, which draws it in its own frame (so it is
 * locked to the world and hidden behind Skyrim geometry): block and fluid meshes built by Minecraft's
 * own block renderer (models, tint, smooth lighting), the texture atlas, arrows and dropped items,
 * and the targeted-block outline. Render thread only.
 */
public final class WorldExporter {
	private static final int SECTIONS_PER_FRAME = 12;
	private static final double ENTITY_RANGE = 96.0;

	private static final LongLinkedOpenHashSet DIRTY = new LongLinkedOpenHashSet();
	private static final LongOpenHashSet SENT = new LongOpenHashSet(); // sections Skyrim holds a mesh for
	private static final LongOpenHashSet LIT = new LongOpenHashSet(); // sections Skyrim holds lights for
	private static final LongOpenHashSet SOLID = new LongOpenHashSet(); // sections Skyrim holds NPC collision for
	private static final long[] SOLID_BITS = new long[64]; // 4096 blocks: bit x + 16z + 256y
	private static final LongOpenHashSet DUG = new LongOpenHashSet(); // sections Skyrim holds dug cells for
	private static final ByteBuffer LIGHTS = ByteBuffer.allocate(16 * 16 * 16 * 8).order(ByteOrder.LITTLE_ENDIAN);
	private static int sentGeneration = Integer.MIN_VALUE;
	private static int meshesSent;
	private static int initialRescanFrames;
	private static volatile boolean ready;
	private static volatile boolean loadedResendRequested;
	private static ClientLevel sentLevel;
	private static SkyAtlas atlas;
	private static BlockRenderDispatcher blockRenderer;
	private static final MeshBuilder MESH = new MeshBuilder();
	private static final List<SkyLink.WorldEntity> ENTITIES = new ArrayList<>();
	private static final java.util.Map<Item, float[]> ICONS = new java.util.HashMap<>();
	private static final java.util.Map<BlockState, float[]> CUBE_FACES = new java.util.HashMap<>();
	private static final RandomSource RANDOM = RandomSource.create();

	private WorldExporter() {
	}

	public static void markDirty(int sx, int sy, int sz) {
		synchronized (DIRTY) {
			DIRTY.add(SectionPos.asLong(sx, sy, sz));
		}
	}

	/** A block the player just placed or broke: re-mesh ahead of everything else. */
	public static void markDirtyNow(int sx, int sy, int sz) {
		synchronized (DIRTY) {
			DIRTY.addAndMoveToFirst(SectionPos.asLong(sx, sy, sz));
		}
	}

	/** Skyrim just finished a load: resend every currently loaded block section before play resumes. */
	public static void requestLoadedResend() {
		loadedResendRequested = true;
		ready = false;
	}

	/** True only after the initial delayed chunk scan and all queued block meshes have been sent. */
	public static boolean ready() {
		return ready;
	}

	public static void frame(Minecraft minecraft, float partialTick) {
		ClientLevel level = minecraft.level;
		if (level == null || minecraft.player == null || !SkyLink.active()) {
			return;
		}
		if (sentGeneration != SkyLink.generation() || sentLevel != level || atlas == null || atlas.stale(minecraft)) {
			resendEverything(minecraft, level);
		}
		if (loadedResendRequested) {
			loadedResendRequested = false;
			int queued = queueLoadedSections(minecraft, level);
			SkyCraft.LOG.info("SkyCraft: Skyrim entered its world; queued {} loaded block sections for resend", queued);
		}
		// A ClientLevel exists slightly before its chunks finish arriving. Scan again after three
		// seconds so a title-screen world cannot report ready with an accidentally empty first scan.
		if (initialRescanFrames > 0 && --initialRescanFrames == 0) {
			int queued = queueLoadedSections(minecraft, level);
			SkyCraft.LOG.info("SkyCraft: delayed world-ready scan queued {} loaded block sections", queued);
		}
		meshDirtySections(level);
		if (initialRescanFrames == 0 && dirtyEmpty()) {
			ready = true;
		}
		// Animated textures (water, lava, fire, ...): the frame for this game tick.
		atlas.animate(level.getGameTime(), region -> {
			ByteBuffer header = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN).putInt(region.x()).putInt(region.y()).putInt(region.w()).putInt(region.h()).flip();
			return SkyLink.tryWriteRender(Proto.REN_ATLAS_REGION, header, region.pixels());
		});
		exportEntities(minecraft, level, partialTick);
		AvatarExporter.frame(minecraft, atlas, partialTick);
	}

	private static void resendEverything(Minecraft minecraft, ClientLevel level) {
		ready = false;
		initialRescanFrames = 180;
		sentGeneration = SkyLink.generation();
		sentLevel = level;
		atlas = SkyAtlas.build(minecraft);
		SkyCraft.LOG.info("SkyCraft: {} animated textures (water, lava, fire, ...) will play in Skyrim", atlas.animatedSprites());
		AvatarExporter.reset();
		ICONS.clear();
		CUBE_FACES.clear();
		blockRenderer = minecraft.getBlockRenderer();
		SkyLink.writeRender(Proto.REN_CLEAR_ALL, ByteBuffer.allocate(0), null);
		boolean ok = sendAtlas(atlas);
		SkyCraft.LOG.info("SkyCraft: sent {}x{} texture atlas to Skyrim ({})", atlas.width, atlas.height, ok ? "ok" : "FAILED");
		SENT.clear();
		LIT.clear();
		SOLID.clear();
		DUG.clear();
		dev.skycraft.client.SkyDigClient.resendAll();
		queueLoadedSections(minecraft, level);
	}

	/** Queue every non-air section currently present in the client's effective render distance. */
	private static int queueLoadedSections(Minecraft minecraft, ClientLevel level) {
		int queued = 0;
		int radius = minecraft.options.getEffectiveRenderDistance() + 1;
		int pcx = SectionPos.blockToSectionCoord(minecraft.player.getBlockX()), pcz = SectionPos.blockToSectionCoord(minecraft.player.getBlockZ());
		for (int cx = pcx - radius; cx <= pcx + radius; cx++) {
			for (int cz = pcz - radius; cz <= pcz + radius; cz++) {
				LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false);
				if (chunk == null) {
					continue;
				}
				LevelChunkSection[] sections = chunk.getSections();
				for (int i = 0; i < sections.length; i++) {
					if (!sections[i].hasOnlyAir()) {
						markDirty(cx, chunk.getSectionYFromSectionIndex(i), cz);
						queued++;
					}
				}
			}
		}
		return queued;
	}

	private static boolean dirtyEmpty() {
		synchronized (DIRTY) {
			return DIRTY.isEmpty();
		}
	}

	/** Large modpacks can produce atlases much bigger than the render ring. Allocate first, then fill it in row strips. */
	private static boolean sendAtlas(SkyAtlas atlas) {
		ByteBuffer size = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
			.putInt(atlas.width).putInt(atlas.height).flip();
		if (!SkyLink.writeRender(Proto.REN_ATLAS_ALLOCATE, size, null)) {
			return false;
		}
		final int maxStripBytes = 16 << 20;
		int rowsPerStrip = Math.max(1, maxStripBytes / (atlas.width * 4));
		for (int y = 0; y < atlas.height; y += rowsPerStrip) {
			int rows = Math.min(rowsPerStrip, atlas.height - y);
			int from = y * atlas.width * 4;
			int bytes = rows * atlas.width * 4;
			ByteBuffer pixels = atlas.pixels.duplicate();
			pixels.position(from).limit(from + bytes);
			ByteBuffer region = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
				.putInt(0).putInt(y).putInt(atlas.width).putInt(rows).flip();
			if (!SkyLink.writeRender(Proto.REN_ATLAS_REGION, region, pixels.slice())) {
				return false;
			}
		}
		return true;
	}

	private static void meshDirtySections(ClientLevel level) {
		// Chunk loads and light updates dirty thousands of all-air sections; those cost a lookup.
		// Real meshing is limited per frame.
		long deadline = System.nanoTime() + 3_000_000L;
		int meshed = 0;
		while (meshed < SECTIONS_PER_FRAME && System.nanoTime() < deadline) {
			long key;
			synchronized (DIRTY) {
				if (DIRTY.isEmpty()) {
					return;
				}
				key = DIRTY.removeFirstLong();
			}
			if (meshSection(level, key)) {
				meshed++;
			}
		}
	}

	private static LevelChunkSection sectionAt(ClientLevel level, LevelChunk chunk, int sy) {
		int index = level.getSectionIndexFromSectionY(sy);
		return index >= 0 && index < chunk.getSections().length ? chunk.getSections()[index] : null;
	}

	/** Returns true if real meshing work was done. */
	private static boolean meshSection(ClientLevel level, long key) {
		int sx = SectionPos.x(key), sy = SectionPos.y(key), sz = SectionPos.z(key);
		LevelChunk chunk = level.getChunkSource().getChunk(sx, sz, ChunkStatus.FULL, false);
		if (chunk == null) {
			return false; // unloaded: Skyrim keeps what it has
		}
		LevelChunkSection section = sectionAt(level, chunk, sy);
		boolean empty = section == null || section.hasOnlyAir();
		long[] dug = dev.skycraft.client.SkyDigClient.dugBits(chunk, sy);
		int dugCount = 0;
		if (dug != null) {
			for (long word : dug) {
				dugCount += Long.bitCount(word);
			}
		}
		if (empty && !SENT.contains(key) && !LIT.contains(key) && !SOLID.contains(key) && dugCount == 0 && !DUG.contains(key)) {
			return false; // nothing there and nothing to remove
		}
		MESH.reset();
		LIGHTS.clear();
		int lightCount = 0;
		java.util.Arrays.fill(SOLID_BITS, 0L);
		int solidCount = 0;
		if (!empty) {
			BlockPos origin = SectionPos.of(sx, sy, sz).origin();
			BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
			for (int y = 0; y < 16; y++) {
				for (int z = 0; z < 16; z++) {
					for (int x = 0; x < 16; x++) {
						pos.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
						BlockState state = chunk.getBlockState(pos);
						if (state.isAir()) {
							continue;
						}
						// Blocks Skyrim's NPCs can't walk through (anything with a collision shape).
						if (!state.getCollisionShape(level, pos).isEmpty()) {
							int bit = x + 16 * z + 256 * y;
							SOLID_BITS[bit >> 6] |= 1L << (bit & 63);
							solidCount++;
						}
						// Light-emitting blocks (torches, lava, glowstone, ...) light Skyrim's world too.
						int emission = state.getLightEmission();
						if (emission > 0) {
							LIGHTS.put((byte) x).put((byte) y).put((byte) z).put((byte) emission).putInt(BlockLightColors.of(state));
							lightCount++;
						}
						FluidState fluid = state.getFluidState();
						if (!fluid.isEmpty()) {
							// Skyrim ground in the cell: the fluid is drawn in the space above it.
							MESH.fluidGround = dev.skycraft.world.SkyCollision.groundTop(pos);
							MESH.fluidBaseY = y;
							MESH.translucent = ItemBlockRenderTypes.getRenderLayer(fluid) == RenderType.translucent();
							blockRenderer.renderLiquid(pos, level, MESH, state, fluid);
							MESH.fluidGround = 0.0F;
						}
						if (state.getRenderShape() == RenderShape.MODEL) {
							PoseStack pose = new PoseStack();
							pose.translate(x, y, z);
							var model = blockRenderer.getBlockModel(state);
							var modelData = model.getModelData(level, pos, state, level.getModelData(pos));
							RANDOM.setSeed(state.getSeed(pos));
							for (RenderType renderType : model.getRenderTypes(state, RANDOM, modelData)) {
								MESH.translucent = renderType == RenderType.translucent();
								blockRenderer.renderBatched(state, pos.immutable(), level, pose, MESH, true, RANDOM, modelData, renderType);
							}
						}
					}
				}
			}
		}
		// Holes dug into Skyrim's ground: Minecraft walls where its surface still runs above them.
		var digLookup = dev.skycraft.world.SkyDig.clientDug;
		if (dugCount > 0 && digLookup != null) {
			Minecraft minecraft = Minecraft.getInstance();
			DigWalls.add(level, sx, sy, sz, dug, digLookup, st -> cubeFaces(minecraft, st), MESH::wall);
		}
		if (MESH.vertexCount() == 0 && !SENT.contains(key) && lightCount == 0 && !LIT.contains(key) && solidCount == 0 && !SOLID.contains(key) && dugCount == 0
			&& !DUG.contains(key)) {
			return true;
		}
		ByteBuffer header = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN).putInt(sx).putInt(sy).putInt(sz).putInt(MESH.vertexCount()).flip();
		if (SkyLink.writeRender(Proto.REN_SECTION, header, MESH.bytes())) {
			if (MESH.vertexCount() > 0) {
				SENT.add(key);
			} else {
				SENT.remove(key);
			}
			if (lightCount > 0 || LIT.contains(key)) {
				ByteBuffer lightHeader = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN).putInt(sx).putInt(sy).putInt(sz).putInt(lightCount).flip();
				if (!SkyLink.writeRender(Proto.REN_LIGHTS, lightHeader, LIGHTS.flip())) {
					markDirty(sx, sy, sz); // ring full; send both again later
				} else if (lightCount > 0) {
					LIT.add(key);
				} else {
					LIT.remove(key);
				}
			}
			if (solidCount > 0 || SOLID.contains(key)) {
				ByteBuffer solidHeader = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN).putInt(sx).putInt(sy).putInt(sz).putInt(solidCount).flip();
				ByteBuffer bits = ByteBuffer.allocate(solidCount > 0 ? 512 : 0).order(ByteOrder.LITTLE_ENDIAN);
				if (solidCount > 0) {
					for (long word : SOLID_BITS) {
						bits.putLong(word);
					}
				}
				if (!SkyLink.writeRender(Proto.REN_SOLIDS, solidHeader, bits.flip())) {
					markDirty(sx, sy, sz);
				} else if (solidCount > 0) {
					SOLID.add(key);
				} else {
					SOLID.remove(key);
				}
			}
			// Cells dug out of Skyrim's world: its geometry there goes.
			if (dugCount > 0 || DUG.contains(key)) {
				ByteBuffer dugHeader = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN).putInt(sx).putInt(sy).putInt(sz).putInt(dugCount)
					.putInt(dev.skycraft.client.SkyDigClient.world()).putInt(0).flip();
				ByteBuffer bits = ByteBuffer.allocate(dugCount > 0 ? 512 : 0).order(ByteOrder.LITTLE_ENDIAN);
				if (dugCount > 0) {
					for (long word : dug) {
						bits.putLong(word);
					}
				}
				if (!SkyLink.writeRender(Proto.REN_DUG, dugHeader, bits.flip())) {
					markDirty(sx, sy, sz);
				} else if (dugCount > 0) {
					DUG.add(key);
				} else {
					DUG.remove(key);
				}
			}
			if (++meshesSent <= 10 || meshesSent % 200 == 0) {
				SkyCraft.LOG.info("SkyCraft: block mesh for section {} {} {}: {} vertices ({} sections in Skyrim)", sx, sy, sz, MESH.vertexCount(), SENT.size());
			}
		} else {
			markDirty(sx, sy, sz); // ring full; try again later
		}
		return true;
	}

	private static void exportEntities(Minecraft minecraft, ClientLevel level, float partialTick) {
		ENTITIES.clear();
		Vec3 eye = minecraft.player.getEyePosition(partialTick);
		for (Entity e : level.entitiesForRendering()) {
			if (ENTITIES.size() >= Proto.MAX_WORLD_ENTITIES || e.distanceToSqr(eye) > ENTITY_RANGE * ENTITY_RANGE) {
				continue;
			}
			Vec3 p = e.getPosition(partialTick);
			if (e instanceof AbstractArrow arrow) {
				boolean trident = arrow instanceof ThrownTrident;
				int kind = trident ? Proto.WE_TRIDENT : Proto.WE_ARROW;
				float yaw = Mth.rotLerp(partialTick, arrow.yRotO, arrow.getYRot());
				float pitch = Mth.lerp(partialTick, arrow.xRotO, arrow.getXRot());
				// Arrows use Minecraft's arrow model and entity texture; tridents their item icon.
				float[] uv = trident ? ICONS.computeIfAbsent(Items.TRIDENT, i -> iconUv(minecraft, level, new ItemStack(i)))
					: atlas.arrowUv(arrow instanceof SpectralArrow ? 2 : arrow instanceof Arrow tippable && tippable.getColor() > 0 ? 1 : 0);
				if (uv != null) {
					ENTITIES.add(new SkyLink.WorldEntity(kind, e.getId(), (float) p.x, (float) p.y, (float) p.z, yaw, pitch, 1.0F, null, uv, 0));
				}
			} else if (e instanceof ItemEntity item) {
				float bob = Mth.sin((item.getAge() + partialTick) / 10.0F + item.bobOffs) * 0.1F + 0.1F;
				float spin = item.getSpin(partialTick) * Mth.RAD_TO_DEG;
				addItem(minecraft, level, e, item.getItem(), p.add(0, bob, 0), spin);
			} else if (e instanceof ItemSupplier supplier) {
				addItem(minecraft, level, e, supplier.getItem(), p.add(0, e.getBbHeight() * 0.5 - 0.25, 0), 0.0F);
			} else if (e instanceof net.minecraft.world.entity.LivingEntity && !(e instanceof dev.skycraft.combat.SkyrimActorEntity) && !e.isInvisible()
				&& (e != minecraft.player || minecraft.gameRenderer.getMainCamera().isDetached())) {
				// Players and mobs: Skyrim darkens the ground softly under their feet.
				ENTITIES.add(new SkyLink.WorldEntity(Proto.WE_SHADOW, e.getId(), (float) p.x, (float) p.y, (float) p.z, 0.0F, 0.0F, e.getBbWidth(), null, null, 0));
			}
		}
		addCracks(level);
		SkyLink.writeWorldEntities(ENTITIES, selection(minecraft, level));
	}

	/**
	 * A dropped item the way Minecraft shows it: blocks as small spinning cubes with their own face
	 * textures, everything else as its icon. {@code p} is the item's resting point (bottom).
	 */
	private static void addItem(Minecraft minecraft, ClientLevel level, Entity e, ItemStack stack, Vec3 p, float yaw) {
		if (stack.getItem() instanceof BlockItem blockItem) {
			BlockState state = blockItem.getBlock().defaultBlockState();
			if (state.getRenderShape() == RenderShape.MODEL
				&& Block.isShapeFullBlock(state.getShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO))) {
				float[] faces = cubeFaces(minecraft, state);
				if (faces != null) {
					float size = 0.25F;
					int tint = cubeTint(minecraft, state);
					ENTITIES.add(new SkyLink.WorldEntity(
						Proto.WE_BLOCK, e.getId(), (float) p.x, (float) (p.y + size * 0.5 + 0.02), (float) p.z, yaw, 0.0F, size, null, faces, tint
					));
					return;
				}
			}
		}
		float[] uv = iconUv(minecraft, level, stack);
		if (uv != null) {
			ENTITIES.add(new SkyLink.WorldEntity(Proto.WE_ITEM, e.getId(), (float) p.x, (float) p.y + 0.25F, (float) p.z, yaw, 0.0F, 0.5F, null, uv, 0));
		}
	}

	/** Side, top and bottom atlas rects of a full-cube block's model, cached per block state. */
	private static float[] cubeFaces(Minecraft minecraft, BlockState state) {
		return CUBE_FACES.computeIfAbsent(state, s -> {
			var model = minecraft.getBlockRenderer().getBlockModel(s);
			RANDOM.setSeed(42);
			float[] out = new float[12];
			Direction[] dirs = { Direction.NORTH, Direction.UP, Direction.DOWN };
			for (int f = 0; f < 3; f++) {
				TextureAtlasSprite sprite = null;
				var quads = model.getQuads(s, dirs[f], RANDOM);
				if (!quads.isEmpty()) {
					sprite = quads.getFirst().getSprite();
				}
				if (sprite == null) {
					return null;
				}
				System.arraycopy(atlas.rect(sprite), 0, out, f * 4, 4);
			}
			return out;
		});
	}

	/** Grass and leaves are grey in the atlas; Minecraft tints them. RGBA8, 0 for none. */
	private static int cubeTint(Minecraft minecraft, BlockState state) {
		int argb = minecraft.getBlockColors().getColor(state, null, null, 0);
		if (argb == -1) {
			return 0;
		}
		return 0xFF000000 | (argb & 0xFF) << 16 | (argb & 0xFF00) | (argb >> 16 & 0xFF);
	}

	/** Cracks over blocks being mined (ours and anyone else's). */
	private static void addCracks(ClientLevel level) {
		for (BlockDestructionProgress progress : ((dev.skycraft.client.mixin.ClientLevelAccessor) Minecraft.getInstance().levelRenderer).skycraft$destroyingBlocks().values()) {
			int stage = progress.getProgress();
			if (stage < 0 || stage > 9 || ENTITIES.size() >= Proto.MAX_WORLD_ENTITIES) {
				continue;
			}
			BlockPos pos = progress.getPos();
			VoxelShape shape = level.getBlockState(pos).getShape(level, pos);
			if (shape.isEmpty()) {
				continue;
			}
			AABB box = shape.bounds().move(pos).inflate(0.004);
			ENTITIES.add(new SkyLink.WorldEntity(
				Proto.WE_CRACK, pos.hashCode(), (float) box.minX, (float) box.minY, (float) box.minZ, 0.0F, 0.0F, 1.0F,
				new float[] { (float) box.getXsize(), (float) box.getYsize(), (float) box.getZsize() }, atlas.crackUv(stage), 0
			));
		}
	}

	/** The item's icon in the combined atlas {u0, v0, u1, v1}, or null. */
	private static float[] iconUv(Minecraft minecraft, ClientLevel level, ItemStack stack) {
		var sprite = minecraft.getItemRenderer().getModel(stack, level, null, 0).getParticleIcon();
		if (sprite == null) {
			return null;
		}
		return atlas.rect(sprite);
	}

	/** Outline for Skyrim to draw: the targeted block, or where a held block would go on Skyrim ground. */
	private static float[] selection(Minecraft minecraft, ClientLevel level) {
		HitResult hit = minecraft.hitResult;
		if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK || minecraft.screen != null) {
			return null;
		}
		BlockPos pos = blockHit.getBlockPos();
		if (blockHit instanceof SkyClip.SkyrimHitResult) {
			if (!(minecraft.player.getMainHandItem().getItem() instanceof BlockItem)) {
				return null;
			}
			return new float[] { pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1 };
		}
		VoxelShape shape = level.getBlockState(pos).getShape(level, pos);
		if (shape.isEmpty()) {
			return null;
		}
		AABB box = shape.bounds().move(pos);
		return new float[] { (float) box.minX, (float) box.minY, (float) box.minZ, (float) box.maxX, (float) box.maxY, (float) box.maxZ };
	}

	/**
	 * Collects Minecraft's block quads (and fluid vertices) as triangles in the RenVertex layout:
	 * section-relative position, combined-atlas UV, RGBA colour (tint and shading), block/sky light.
	 */
	private static final class MeshBuilder implements VertexConsumer {
		private ByteBuffer buf = ByteBuffer.allocateDirect(1 << 20).order(ByteOrder.LITTLE_ENDIAN);
		private int vertices;
		// Minecraft emits quads; SkyCraft's wire format uses triangles.
		private final float[] fq = new float[4 * 8];
		private int fqCount;
		private float x, y, z, u, v, nx, ny, nz;
		private int color = 0xFFFFFFFF, light;
		boolean translucent;

		void reset() {
			this.buf.clear();
			this.vertices = 0;
			this.fqCount = 0;
		}

		int vertexCount() {
			return this.vertices;
		}

		ByteBuffer bytes() {
			return this.buf.duplicate().flip();
		}

		private void ensure(int bytes) {
			if (this.buf.remaining() < bytes) {
				ByteBuffer bigger = ByteBuffer.allocateDirect(Math.max(this.buf.capacity() * 2, this.buf.position() + bytes)).order(ByteOrder.LITTLE_ENDIAN);
				this.buf.flip();
				bigger.put(this.buf);
				this.buf = bigger;
			}
		}

		private void vertex(float x, float y, float z, float u, float v, int argb, int light, int flags) {
			this.buf.putFloat(x).putFloat(y).putFloat(z).putFloat(u).putFloat(v);
			this.buf.put((byte) (argb >> 16)).put((byte) (argb >> 8)).put((byte) argb).put((byte) (argb >>> 24));
			int block = (light >> 4) & 0xF;
			int sky = (light >> 20) & 0xF;
			this.buf.putInt(block | (sky << 8));
			this.buf.putInt(flags);
			this.vertices++;
		}

		/**
		 * Vertex flags: cutout or translucent, plus the face normal (Direction ordinal + 1) that
		 * Skyrim lights the face with. 0 leaves it without a normal (plants and other quads Minecraft
		 * doesn't shade by direction).
		 */
		private static int flags(boolean translucent, Direction normal) {
			return (translucent ? 2 : 1) | (normal == null ? 0 : (normal.ordinal() + 1) << 4);
		}

		/** A dug hole's wall (DigWalls): an untinted opaque quad. */
		void wall(float[] xyz, float[] uv, int light, Direction normal) {
			this.ensure(6 * Proto.REN_VERTEX_BYTES);
			int flags = flags(false, normal);
			for (int k : new int[] { 0, 1, 2, 0, 2, 3 }) {
				this.vertex(xyz[k * 3], xyz[k * 3 + 1], xyz[k * 3 + 2], uv[k * 2], uv[k * 2 + 1], 0xFFFFFFFF, light, flags);
			}
		}

		// Skyrim ground's height in a fluid cell (0..1).
		// Skyrim ground's height in the fluid's cell (0..1) and the cell's section-relative y: a
		// Minecraft fluid level counts from the cell's floor, so on Skyrim ground partway up the cell
		// the fluid is squeezed into the space above it (thin edges stay visible on the ground).
		float fluidGround;
		int fluidBaseY;

		private void finishVertex() {
			float x = this.x, y = this.y, z = this.z;
			int o = this.fqCount * 8;
			if (this.fluidGround > 0.0F) {
				float t = Math.max(0.0F, Math.min(1.0F, y - this.fluidBaseY));
				y = this.fluidBaseY + this.fluidGround + t * (1.0F - this.fluidGround);
			}
			this.fq[o] = x;
			this.fq[o + 1] = y;
			this.fq[o + 2] = z;
			this.fq[o + 3] = atlas.blockU(this.u);
			this.fq[o + 4] = atlas.blockV(this.v);
			this.fq[o + 5] = Float.intBitsToFloat(this.color);
			this.fq[o + 6] = Float.intBitsToFloat(this.light);
			this.fq[o + 7] = Float.intBitsToFloat(flags(this.translucent,
				this.nx == 0 && this.ny == 0 && this.nz == 0 ? null : Direction.getNearest(this.nx, this.ny, this.nz)));
			if (++this.fqCount == 4) {
				this.fqCount = 0;
				this.ensure(6 * Proto.REN_VERTEX_BYTES);
				for (int k : new int[] { 0, 1, 2, 0, 2, 3 }) {
					int b = k * 8;
					this.vertex(this.fq[b], this.fq[b + 1], this.fq[b + 2], this.fq[b + 3], this.fq[b + 4],
						Float.floatToRawIntBits(this.fq[b + 5]), Float.floatToRawIntBits(this.fq[b + 6]), Float.floatToRawIntBits(this.fq[b + 7]));
				}
			}
		}

		@Override
		public VertexConsumer addVertex(float x, float y, float z) {
			this.x = x;
			this.y = y;
			this.z = z;
			this.color = 0xFFFFFFFF;
			this.light = 0;
			this.u = this.v = this.nx = this.ny = this.nz = 0;
			return this;
		}

		@Override
		public VertexConsumer setColor(int r, int g, int b, int a) {
			this.color = (a & 255) << 24 | (r & 255) << 16 | (g & 255) << 8 | (b & 255);
			return this;
		}

		@Override
		public VertexConsumer setUv(float u, float v) {
			this.u = u;
			this.v = v;
			return this;
		}

		@Override
		public VertexConsumer setUv1(int u, int v) {
			return this;
		}

		@Override
		public VertexConsumer setUv2(int u, int v) {
			this.light = (u & 0xFFFF) | (v & 0xFFFF) << 16;
			return this;
		}

		@Override
		public VertexConsumer setNormal(float x, float y, float z) {
			this.nx = x;
			this.ny = y;
			this.nz = z;
			this.finishVertex();
			return this;
		}
	}
}
