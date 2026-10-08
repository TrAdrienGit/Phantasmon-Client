package com.mystaria.phantasmon.client.hub;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

import com.mystaria.phantasmon.client.auth.AuthSession;

/**
 * The Global Hub's build (D-34): around every Global Hub anchor of the current server and dimension, the backend's
 * schematic ({@code GET /hub/schematic}) is built as <b>client-only</b> blocks — real blocks of this client's world
 * (same rendering, lighting, shaders, collisions), unknown to the Minecraft server.
 *
 * <ul>
 * <li>The file is downloaded once and cached by its SHA-256 in {@code config/phantasmon-hub-schematics/}.</li>
 * <li>Only the schematic's non-air blocks are placed: the cube was checked empty when the anchor was posed, and air
 * never hides a real block (the server would still collide with it).</li>
 * <li>The server knows nothing of them, so whatever it sends for those positions (a block update, the chunk again
 * when it comes back in view) is kept aside ({@code ClientLevelMixin}) and the build is put back; the real state
 * returns when the anchor is deleted.</li>
 * <li>They can't be broken nor used to place a block ({@code MultiPlayerGameModeMixin}); doors, trapdoors and fence
 * gates open and close here only.</li>
 * </ul>
 * Client thread only, except the download.
 */
public final class HubBuilds {

	private static final Logger LOG = LoggerFactory.getLogger(HubBuilds.class);
	private static final Path CACHE_DIR = FabricLoader.getInstance().getConfigDir().resolve("phantasmon-hub-schematics");
	/** Tell the renderer, but no neighbour shape updates: real blocks around are left as the server made them. */
	private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
	private static final Rotation[] ROTATIONS = { Rotation.NONE, Rotation.CLOCKWISE_90, Rotation.CLOCKWISE_180,
			Rotation.COUNTERCLOCKWISE_90 };
	/** After a failed download, try again this much later. */
	private static final long RETRY_MILLIS = 60_000;

	private static HubBuilds instance;

	private record Resolved(int x, int y, int z, BlockState state, CompoundTag blockEntity) {
	}

	/** A client-only block: its anchor, what stands there now (a door may have been opened), its block entity data. */
	private static final class Placed {
		final UUID anchor;
		BlockState state;
		final CompoundTag blockEntity;

		Placed(UUID anchor, BlockState state, CompoundTag blockEntity) {
			this.anchor = anchor;
			this.state = state;
			this.blockEntity = blockEntity;
		}
	}

	private final HubClient hubClient;
	private final AuthSession session;

	private String sha256;
	private int schematicSize;
	private List<Resolved> resolved;
	private boolean fetching;
	private long nextFetchAt;

	private ClientLevel level;
	private List<HubAnchorDto> anchors = List.of();
	/** Anchor → its layout, to notice an anchor that moved (deleted and posed again under the same uuid never happens, but cheap). */
	private final Map<UUID, HubBuildLayout> layouts = new HashMap<>();
	private final Map<UUID, List<Long>> byAnchor = new HashMap<>();
	/** {@code BlockPos.asLong} → client-only block. */
	private final Map<Long, Placed> fake = new HashMap<>();
	/** {@code BlockPos.asLong} → what the server says stands there (air unless it sent something else). */
	private final Map<Long, BlockState> server = new HashMap<>();
	/** {@code ChunkPos.toLong} → the client-only blocks in that chunk. */
	private final Map<Long, List<Long>> byChunk = new HashMap<>();
	/** Chunks to (re)build at the next tick: new anchors, chunks the server (re)sent. */
	private final Set<Long> dirtyChunks = new LinkedHashSet<>();

	public HubBuilds(HubClient hubClient, AuthSession session) {
		this.hubClient = hubClient;
		this.session = session;
		instance = this;
	}

	// ---------------------------------------------------------------- schematic

	/** Makes sure the backend's current schematic is here (asks the backend; downloads only when it changed). */
	public void refreshSchematic() {
		if (fetching || !session.isAuthenticated() || System.currentTimeMillis() < nextFetchAt) {
			return;
		}
		fetching = true;
		String token = session.accessToken();
		hubClient.schematic(token)
				.thenCompose(dto -> dto.sha256().equals(sha256) ? CompletableFuture.<Downloaded>completedFuture(null) : load(dto, token))
				.whenComplete((schematic, error) -> Minecraft.getInstance().execute(() -> {
					fetching = false;
					if (error != null) {
						nextFetchAt = System.currentTimeMillis() + RETRY_MILLIS;
						LOG.warn("Could not load the Global Hub schematic", error);
					} else if (schematic != null) {
						install(schematic);
					}
				}));
	}

	private record Downloaded(HubSchematicDto dto, HubSchematic schematic) {
	}

	private CompletableFuture<Downloaded> load(HubSchematicDto dto, String token) {
		Path cached = CACHE_DIR.resolve(dto.sha256() + (dto.litematic() ? ".litematic" : ".schem"));
		CompletableFuture<byte[]> bytes;
		try {
			bytes = Files.isRegularFile(cached) && dto.sha256().equals(sha256(Files.readAllBytes(cached)))
					? CompletableFuture.completedFuture(Files.readAllBytes(cached))
					: hubClient.schematicFile(token).thenApply(file -> {
						if (!dto.sha256().equals(sha256(file))) {
							throw new IllegalStateException("Hub schematic download does not match its SHA-256");
						}
						try {
							Files.createDirectories(CACHE_DIR);
							Files.write(cached, file);
						} catch (IOException ex) {
							LOG.warn("Could not cache the Hub schematic in {}", cached, ex);
						}
						return file;
					});
		} catch (IOException ex) {
			return CompletableFuture.failedFuture(ex);
		}
		return bytes.thenApply(file -> {
			try {
				return new Downloaded(dto, HubSchematic.read(file, dto.litematic()));
			} catch (IOException ex) {
				throw new IllegalStateException("Unreadable Hub schematic " + dto.name(), ex);
			}
		});
	}

	/** Client thread: block names → this client's blocks (unknown ones become air and are left out). */
	private void install(Downloaded downloaded) {
		var blocks = BuiltInRegistries.BLOCK.asLookup();
		List<Resolved> list = new ArrayList<>();
		int unknown = 0;
		for (HubSchematic.Block block : downloaded.schematic().blocks()) {
			BlockState state;
			try {
				state = NbtUtils.readBlockState(blocks, block.state());
			} catch (RuntimeException ex) {
				state = Blocks.AIR.defaultBlockState();
			}
			if (state.isAir()) {
				unknown++;
				continue;
			}
			list.add(new Resolved(block.x(), block.y(), block.z(), state, block.blockEntity()));
		}
		if (unknown > 0) {
			LOG.warn("Hub schematic {}: {} block(s) unknown to this client, left out", downloaded.dto().name(), unknown);
		}
		LOG.info("Hub schematic {} loaded: {} blocks", downloaded.dto().name(), list.size());
		removeAll();
		sha256 = downloaded.dto().sha256();
		schematicSize = downloaded.schematic().sizeX();
		resolved = List.copyOf(list);
		sync();
	}

	// ---------------------------------------------------------------- anchors

	/** The anchors of the current server and dimension changed (or were listed again). */
	public void setAnchors(List<HubAnchorDto> list) {
		anchors = List.copyOf(list);
		sync();
	}

	/** World left: the blocks went with it. */
	public void reset() {
		forgetWorld();
		level = null;
		anchors = List.of();
	}

	private void sync() {
		ClientLevel current = Minecraft.getInstance().level;
		if (current != level) {
			forgetWorld();
			level = current;
		}
		if (level == null) {
			return;
		}
		Map<UUID, HubBuildLayout> wanted = new HashMap<>();
		if (resolved != null) {
			for (HubAnchorDto anchor : anchors) {
				if (anchor.size() != schematicSize) {
					LOG.warn("Hub anchor {} is {} blocks wide, the schematic {}: not built", anchor.name(), anchor.size(), schematicSize);
					continue;
				}
				wanted.put(anchor.uuid(), HubBuildLayout.of(anchor.origin().x(), anchor.origin().y(), anchor.origin().z(),
						anchor.yaw(), anchor.size()));
			}
		}
		for (UUID anchor : List.copyOf(byAnchor.keySet())) {
			if (!layouts.get(anchor).equals(wanted.get(anchor))) {
				remove(anchor);
			}
		}
		for (Map.Entry<UUID, HubBuildLayout> entry : wanted.entrySet()) {
			if (!byAnchor.containsKey(entry.getKey())) {
				place(entry.getKey(), entry.getValue());
			}
		}
	}

	private void place(UUID anchor, HubBuildLayout layout) {
		Rotation rotation = ROTATIONS[layout.quarterTurns()];
		List<Long> positions = new ArrayList<>();
		for (Resolved block : resolved) {
			int[] world = layout.toWorld(block.x(), block.y(), block.z());
			long key = BlockPos.asLong(world[0], world[1], world[2]);
			if (fake.containsKey(key)) {
				continue; // two anchors' cubes overlap (posed before the empty-cube check): the first one keeps it
			}
			fake.put(key, new Placed(anchor, block.state().rotate(rotation), block.blockEntity()));
			positions.add(key);
			long chunk = ChunkPos.asLong(world[0] >> 4, world[2] >> 4);
			byChunk.computeIfAbsent(chunk, ignored -> new ArrayList<>()).add(key);
			dirtyChunks.add(chunk);
		}
		byAnchor.put(anchor, positions);
		layouts.put(anchor, layout);
	}

	/** The anchor is gone: what the server says stands there comes back. */
	private void remove(UUID anchor) {
		layouts.remove(anchor);
		List<Long> positions = byAnchor.remove(anchor);
		if (positions == null) {
			return;
		}
		for (long key : positions) {
			Placed placed = fake.remove(key);
			BlockState real = server.remove(key);
			BlockPos pos = BlockPos.of(key);
			List<Long> inChunk = byChunk.get(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
			if (inChunk != null) {
				inChunk.remove(key);
			}
			if (placed != null && level != null && loaded(pos) && level.getBlockState(pos) == placed.state) {
				level.setBlock(pos, real == null ? Blocks.AIR.defaultBlockState() : real, FLAGS);
			}
		}
	}

	private void removeAll() {
		List.copyOf(byAnchor.keySet()).forEach(this::remove);
	}

	private void forgetWorld() {
		layouts.clear();
		byAnchor.clear();
		fake.clear();
		server.clear();
		byChunk.clear();
		dirtyChunks.clear();
	}

	// ---------------------------------------------------------------- world

	/** Every client tick: builds the chunks that need it, once they're loaded. */
	public void tick() {
		if (Minecraft.getInstance().level != level) {
			sync();
		}
		if (level == null || dirtyChunks.isEmpty()) {
			return;
		}
		boolean built = false;
		for (Long chunk : List.copyOf(dirtyChunks)) {
			if (!level.getChunkSource().hasChunk(ChunkPos.getX(chunk), ChunkPos.getZ(chunk))) {
				continue; // not in view: built when the server sends it
			}
			dirtyChunks.remove(chunk);
			for (long key : byChunk.getOrDefault(chunk, List.of())) {
				built |= apply(key, fake.get(key));
			}
		}
		if (built) {
			liftPlayerOutOfTheFloor();
		}
	}

	private boolean apply(long key, Placed placed) {
		if (placed == null) {
			return false;
		}
		BlockPos pos = BlockPos.of(key);
		BlockState current = level.getBlockState(pos);
		if (current == placed.state) {
			return false;
		}
		// What stands there now came from the server (a fresh chunk): kept to give it back later.
		server.put(key, current);
		level.setBlock(pos, placed.state, FLAGS);
		if (placed.blockEntity != null) {
			BlockEntity blockEntity = level.getBlockEntity(pos);
			if (blockEntity != null) {
				try {
					blockEntity.loadWithComponents(placed.blockEntity, level.registryAccess());
				} catch (RuntimeException ex) {
					LOG.debug("Hub build: block entity data at {} not applied", pos, ex);
				}
			}
		}
		return true;
	}

	/** The floor appeared under (or around) our feet — right after posing the anchor: step on top of it. */
	private void liftPlayerOutOfTheFloor() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || player.level() != level) {
			return;
		}
		BlockPos feet = player.blockPosition();
		if (!fake.containsKey(feet.asLong())) {
			return;
		}
		VoxelShape shape = level.getBlockState(feet).getCollisionShape(level, feet);
		if (!shape.isEmpty()) {
			double top = feet.getY() + shape.max(Direction.Axis.Y);
			if (top > player.getY()) {
				player.setPos(player.getX(), top, player.getZ());
			}
		}
	}

	/** Fabric's chunk load: the server (re)sent a chunk, its client-only blocks are gone — built again next tick. */
	public static void onChunkLoad(ClientLevel chunkLevel, ChunkPos chunkPos) {
		HubBuilds builds = instance;
		if (builds != null && chunkLevel == builds.level && builds.byChunk.containsKey(chunkPos.toLong())) {
			builds.dirtyChunks.add(chunkPos.toLong());
		}
	}

	/** {@code ClientLevelMixin}: the server sets a block; at a client-only block it is kept aside instead. */
	public static boolean interceptServerBlock(ClientLevel serverLevel, BlockPos pos, BlockState state) {
		HubBuilds builds = instance;
		if (builds == null || serverLevel != builds.level) {
			return false;
		}
		long key = pos.asLong();
		if (!builds.fake.containsKey(key)) {
			return false;
		}
		builds.server.put(key, state);
		return true;
	}

	/** Whether this position holds a client-only block of a Hub build (can't be broken nor built against). */
	public static boolean isFake(ClientLevel atLevel, BlockPos pos) {
		HubBuilds builds = instance;
		return builds != null && atLevel == builds.level && builds.fake.containsKey(pos.asLong());
	}

	/**
	 * {@code MultiPlayerGameModeMixin}, right click on a client-only block: doors, trapdoors and fence gates open or
	 * close here only ({@link InteractionResult#SUCCESS}); anything else does nothing on it ({@link InteractionResult#PASS},
	 * so the held item may still be used in the air) — never sent to the server, which would build against it.
	 */
	public static InteractionResult use(ClientLevel atLevel, BlockHitResult hit) {
		HubBuilds builds = instance;
		BlockPos pos = hit.getBlockPos();
		BlockState state = atLevel.getBlockState(pos);
		Placed placed = builds == null ? null : builds.fake.get(pos.asLong());
		if (placed == null || !state.hasProperty(BlockStateProperties.OPEN)) {
			return InteractionResult.PASS;
		}
		boolean open = !state.getValue(BlockStateProperties.OPEN);
		SoundEvent sound;
		if (state.getBlock() instanceof DoorBlock door) {
			if (!door.type().canOpenByHand()) {
				return InteractionResult.PASS;
			}
			sound = open ? door.type().doorOpen() : door.type().doorClose();
			BlockPos other = state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
			builds.setOpen(other, open);
		} else if (state.getBlock() instanceof TrapDoorBlock) {
			if (state.is(Blocks.IRON_TRAPDOOR)) {
				return InteractionResult.PASS;
			}
			sound = open ? SoundEvents.WOODEN_TRAPDOOR_OPEN : SoundEvents.WOODEN_TRAPDOOR_CLOSE;
		} else if (state.getBlock() instanceof FenceGateBlock) {
			sound = open ? SoundEvents.FENCE_GATE_OPEN : SoundEvents.FENCE_GATE_CLOSE;
		} else {
			return InteractionResult.PASS;
		}
		builds.setOpen(pos, open);
		atLevel.playLocalSound(pos, sound, SoundSource.BLOCKS, 1.0F, atLevel.getRandom().nextFloat() * 0.1F + 0.9F, false);
		return InteractionResult.SUCCESS;
	}

	private void setOpen(BlockPos pos, boolean open) {
		Placed placed = fake.get(pos.asLong());
		if (placed == null || !placed.state.hasProperty(BlockStateProperties.OPEN)) {
			return;
		}
		placed.state = placed.state.setValue(BlockStateProperties.OPEN, open);
		if (level != null && loaded(pos)) {
			level.setBlock(pos, placed.state, FLAGS);
		}
	}

	private boolean loaded(BlockPos pos) {
		return level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4);
	}

	// ---------------------------------------------------------------- the check before posing an anchor

	/** Number of non-air blocks in the cube an anchor posed here would build in (0: it may be posed). */
	public static int blocksInTheWay(ClientLevel atLevel, HubBuildLayout layout) {
		int[] min = layout.min();
		int[] max = layout.max();
		int count = 0;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int x = min[0]; x <= max[0]; x++) {
			for (int y = min[1]; y <= max[1]; y++) {
				for (int z = min[2]; z <= max[2]; z++) {
					if (!atLevel.getBlockState(pos.set(x, y, z)).isAir()) {
						count++;
					}
				}
			}
		}
		return count;
	}

	private static String sha256(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		} catch (java.security.NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}
}
