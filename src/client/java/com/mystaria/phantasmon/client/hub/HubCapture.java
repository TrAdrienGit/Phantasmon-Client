package com.mystaria.phantasmon.client.hub;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.SharedConstants;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Copies what stands in an anchor's zone into a Sponge {@code .schem} v3 of the hub's size (Adrien 2026-11-10: admins
 * build a hub in the game instead of in WorldEdit / Litematica). Schematic block {@code (x, y, z)} is read at
 * {@link HubBuildLayout#toWorld} and turned back by the anchor's quarter turns, so the copy is the build as the anchor
 * shows it, whatever way it faces. The bottom layer is the ground under the anchor (the floor layer, D-34), copied too.
 * Block entities (chests, signs...) are kept; entities are not. What this client sees is copied: the real blocks and
 * the current build's client-only ones.
 */
public final class HubCapture {

	private static final Rotation[] INVERSE = { Rotation.NONE, Rotation.COUNTERCLOCKWISE_90, Rotation.CLOCKWISE_180,
			Rotation.CLOCKWISE_90 };

	private HubCapture() {
	}

	/** Whether every chunk of the zone is loaded here (otherwise its blocks can't be read). */
	public static boolean loaded(ClientLevel level, HubBuildLayout layout) {
		int[] min = layout.min();
		int[] max = layout.max();
		for (int cx = min[0] >> 4; cx <= max[0] >> 4; cx++) {
			for (int cz = min[2] >> 4; cz <= max[2] >> 4; cz++) {
				if (!level.getChunkSource().hasChunk(cx, cz)) {
					return false;
				}
			}
		}
		return true;
	}

	/** The zone as a gzipped Sponge v3 schematic. Client thread. */
	public static byte[] capture(ClientLevel level, HubBuildLayout layout) throws IOException {
		int width = layout.sizeX();
		int height = layout.sizeY();
		int length = layout.sizeZ();
		Rotation back = INVERSE[layout.quarterTurns()];
		Map<String, Integer> palette = new LinkedHashMap<>();
		ByteArrayOutputStream data = new ByteArrayOutputStream();
		ListTag blockEntities = new ListTag();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int y = 0; y < height; y++) {
			for (int z = 0; z < length; z++) {
				for (int x = 0; x < width; x++) {
					int[] world = layout.toWorld(x, y, z);
					pos.set(world[0], world[1], world[2]);
					BlockState state = level.getBlockState(pos).rotate(back);
					String key = BlockStateParser.serialize(state);
					int id = palette.computeIfAbsent(key, ignored -> palette.size());
					writeVarInt(data, id);
					BlockEntity blockEntity = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;
					if (blockEntity != null) {
						CompoundTag entity = new CompoundTag();
						entity.putIntArray("Pos", new int[] { x, y, z });
						entity.putString("Id", String.valueOf(BlockEntityType.getKey(blockEntity.getType())));
						entity.put("Data", blockEntity.saveWithoutMetadata(level.registryAccess()));
						blockEntities.add(entity);
					}
				}
			}
		}
		CompoundTag paletteTag = new CompoundTag();
		palette.forEach(paletteTag::putInt);
		CompoundTag blocks = new CompoundTag();
		blocks.put("Palette", paletteTag);
		blocks.putByteArray("Data", data.toByteArray());
		blocks.put("BlockEntities", blockEntities);
		CompoundTag schematic = new CompoundTag();
		schematic.putInt("Version", 3);
		schematic.putInt("DataVersion", SharedConstants.getCurrentVersion().getDataVersion().getVersion());
		schematic.putShort("Width", (short) width);
		schematic.putShort("Height", (short) height);
		schematic.putShort("Length", (short) length);
		schematic.putIntArray("Offset", new int[] { 0, 0, 0 });
		schematic.put("Blocks", blocks);
		CompoundTag root = new CompoundTag();
		root.put("Schematic", schematic);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		NbtIo.writeCompressed(root, out);
		return out.toByteArray();
	}

	private static void writeVarInt(ByteArrayOutputStream out, int value) {
		while ((value & ~0x7F) != 0) {
			out.write((value & 0x7F) | 0x80);
			value >>>= 7;
		}
		out.write(value);
	}
}
