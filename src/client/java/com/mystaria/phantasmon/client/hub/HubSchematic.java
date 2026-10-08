package com.mystaria.phantasmon.client.hub;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

/**
 * The Global Hub's build (D-34) as read from its file: a box of {@code sizeX × sizeY × sizeZ} blocks and every
 * non-air block in it, with its state as Minecraft's NBT shape ({@code {Name, Properties}}) and its block entity data
 * if any. Only {@code net.minecraft.nbt} here — no registry, no game bootstrap — so it stays unit-testable; turning
 * states into {@code BlockState}s (unknown blocks → air) is {@link HubBuilds}' job.
 *
 * <p>Formats: Sponge {@code .schem} v1 / v2 (palette + varint {@code BlockData}, root named "Schematic") and v3 (the same
 * in {@code Schematic.Blocks}); Litematica {@code .litematic} (every region, bit-packed {@code BlockStates}, placed in
 * the enclosing box — a region's negative size runs backwards from its position).
 */
public record HubSchematic(int sizeX, int sizeY, int sizeZ, List<Block> blocks) {

	/** One block, {@code (x, y, z)} from the box's minimum corner. {@code blockEntity}: null, or its data with "id". */
	public record Block(int x, int y, int z, CompoundTag state, CompoundTag blockEntity) {

		public String name() {
			return state.getString("Name");
		}
	}

	public static HubSchematic read(byte[] file, boolean litematic) throws IOException {
		CompoundTag root = NbtIo.readCompressed(new ByteArrayInputStream(file), NbtAccounter.unlimitedHeap());
		return litematic ? readLitematic(root) : readSponge(root);
	}

	// ---- Sponge ----

	private static HubSchematic readSponge(CompoundTag root) throws IOException {
		CompoundTag schematic = root.contains("Schematic", Tag.TAG_COMPOUND) ? root.getCompound("Schematic") : root;
		int version = schematic.getInt("Version");
		int width = Short.toUnsignedInt(schematic.getShort("Width"));
		int height = Short.toUnsignedInt(schematic.getShort("Height"));
		int length = Short.toUnsignedInt(schematic.getShort("Length"));
		CompoundTag container = version >= 3 ? schematic.getCompound("Blocks") : schematic;
		CompoundTag paletteTag = container.getCompound("Palette");
		byte[] data = container.getByteArray(version >= 3 ? "Data" : "BlockData");
		Map<Integer, CompoundTag> palette = new HashMap<>();
		for (String state : paletteTag.getAllKeys()) {
			palette.put(paletteTag.getInt(state), stateTag(state));
		}

		Map<Long, CompoundTag> blockEntities = new HashMap<>();
		String entitiesKey = version >= 3 || container.contains("BlockEntities") ? "BlockEntities" : "TileEntities";
		for (Tag tag : container.getList(entitiesKey, Tag.TAG_COMPOUND)) {
			CompoundTag entity = (CompoundTag) tag;
			int[] pos = entity.getIntArray("Pos");
			if (pos.length != 3) {
				continue;
			}
			// v3 keeps the data in "Data"; v1 / v2 inline it next to Pos and Id.
			CompoundTag nbt = version >= 3 && entity.contains("Data", Tag.TAG_COMPOUND) ? entity.getCompound("Data").copy() : entity.copy();
			nbt.remove("Pos");
			nbt.remove("Data");
			nbt.remove("Id");
			nbt.putString("id", entity.getString("Id"));
			blockEntities.put(key(pos[0], pos[1], pos[2]), nbt);
		}

		List<Block> blocks = new ArrayList<>();
		int index = 0;
		int position = 0;
		int volume = width * height * length;
		while (position < data.length && index < volume) {
			int value = 0;
			int shift = 0;
			byte b;
			do {
				if (position >= data.length) {
					throw new IOException("Truncated block data");
				}
				b = data[position++];
				value |= (b & 0x7F) << shift;
				shift += 7;
			} while ((b & 0x80) != 0);
			CompoundTag state = palette.get(value);
			if (state == null) {
				throw new IOException("Block data refers to palette entry " + value + " which does not exist");
			}
			int x = index % width;
			int z = (index / width) % length;
			int y = index / (width * length);
			if (!isAir(state)) {
				blocks.add(new Block(x, y, z, state, blockEntities.get(key(x, y, z))));
			}
			index++;
		}
		return new HubSchematic(width, height, length, List.copyOf(blocks));
	}

	/** {@code minecraft:oak_stairs[facing=east,half=bottom]} → {@code {Name: ..., Properties: {facing: east, ...}}}. */
	static CompoundTag stateTag(String state) {
		CompoundTag tag = new CompoundTag();
		int bracket = state.indexOf('[');
		String name = (bracket < 0 ? state : state.substring(0, bracket)).trim();
		tag.putString("Name", name.contains(":") ? name : "minecraft:" + name);
		if (bracket >= 0 && state.endsWith("]")) {
			CompoundTag properties = new CompoundTag();
			for (String pair : state.substring(bracket + 1, state.length() - 1).split(",")) {
				int equals = pair.indexOf('=');
				if (equals > 0) {
					properties.putString(pair.substring(0, equals).trim(), pair.substring(equals + 1).trim());
				}
			}
			if (!properties.isEmpty()) {
				tag.put("Properties", properties);
			}
		}
		return tag;
	}

	// ---- Litematica ----

	private static HubSchematic readLitematic(CompoundTag root) throws IOException {
		CompoundTag regions = root.getCompound("Regions");
		record Region(CompoundTag tag, int minX, int minY, int minZ, int sizeX, int sizeY, int sizeZ) {
		}
		List<Region> list = new ArrayList<>();
		int minX = Integer.MAX_VALUE;
		int minY = Integer.MAX_VALUE;
		int minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int maxY = Integer.MIN_VALUE;
		int maxZ = Integer.MIN_VALUE;
		for (String name : regions.getAllKeys()) {
			CompoundTag region = regions.getCompound(name);
			CompoundTag position = region.getCompound("Position");
			CompoundTag size = region.getCompound("Size");
			int[] start = new int[3];
			int[] extent = new int[3];
			String[] axes = { "x", "y", "z" };
			for (int i = 0; i < 3; i++) {
				int p = position.getInt(axes[i]);
				int s = size.getInt(axes[i]);
				start[i] = s < 0 ? p + s + 1 : p;
				extent[i] = Math.abs(s);
			}
			list.add(new Region(region, start[0], start[1], start[2], extent[0], extent[1], extent[2]));
			minX = Math.min(minX, start[0]);
			minY = Math.min(minY, start[1]);
			minZ = Math.min(minZ, start[2]);
			maxX = Math.max(maxX, start[0] + extent[0]);
			maxY = Math.max(maxY, start[1] + extent[1]);
			maxZ = Math.max(maxZ, start[2] + extent[2]);
		}
		if (list.isEmpty()) {
			throw new IOException("Litematic without any region");
		}

		List<Block> blocks = new ArrayList<>();
		for (Region region : list) {
			ListTag paletteTag = region.tag().getList("BlockStatePalette", Tag.TAG_COMPOUND);
			List<CompoundTag> palette = new ArrayList<>();
			for (Tag tag : paletteTag) {
				palette.add((CompoundTag) tag);
			}
			Map<Long, CompoundTag> blockEntities = new HashMap<>();
			for (Tag tag : region.tag().getList("TileEntities", Tag.TAG_COMPOUND)) {
				CompoundTag entity = ((CompoundTag) tag).copy();
				long key = key(entity.getInt("x"), entity.getInt("y"), entity.getInt("z"));
				entity.remove("x");
				entity.remove("y");
				entity.remove("z");
				blockEntities.put(key, entity);
			}
			long[] states = region.tag().getLongArray("BlockStates");
			int bits = Math.max(2, Integer.SIZE - Integer.numberOfLeadingZeros(Math.max(1, palette.size()) - 1));
			long mask = (1L << bits) - 1;
			int volume = region.sizeX() * region.sizeY() * region.sizeZ();
			if ((long) volume * bits > (long) states.length * 64) {
				throw new IOException("Truncated litematic region");
			}
			for (int index = 0; index < volume; index++) {
				long start = (long) index * bits;
				int word = (int) (start >>> 6);
				int offset = (int) (start & 63);
				long value = states[word] >>> offset;
				if (offset + bits > 64) {
					value |= states[word + 1] << (64 - offset);
				}
				int id = (int) (value & mask);
				if (id >= palette.size()) {
					throw new IOException("Block state " + id + " outside the palette");
				}
				CompoundTag state = palette.get(id);
				if (isAir(state)) {
					continue;
				}
				int x = index % region.sizeX();
				int z = (index / region.sizeX()) % region.sizeZ();
				int y = index / (region.sizeX() * region.sizeZ());
				blocks.add(new Block(region.minX() - minX + x, region.minY() - minY + y, region.minZ() - minZ + z, state,
						blockEntities.get(key(x, y, z))));
			}
		}
		return new HubSchematic(maxX - minX, maxY - minY, maxZ - minZ, List.copyOf(blocks));
	}

	private static boolean isAir(CompoundTag state) {
		String name = state.getString("Name").toLowerCase(Locale.ROOT);
		return name.equals("minecraft:air") || name.equals("minecraft:cave_air") || name.equals("minecraft:void_air")
				|| name.equals("air");
	}

	private static long key(int x, int y, int z) {
		return ((long) x & 0x1FFFFF) << 42 | ((long) y & 0x1FFFFF) << 21 | ((long) z & 0x1FFFFF);
	}
}
