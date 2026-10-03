package com.mystaria.phantasmon.client.battle;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cobblemon.mod.common.CobblemonNetwork;
import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler;
import com.cobblemon.mod.common.api.net.NetworkPacket;
import com.cobblemon.mod.common.net.PacketRegisterInfo;

import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * Feeds Cobblemon's own client battle UI with packets that never came from
 * the Minecraft server (Phase 9): the battle runs on the host client, and
 * whatever Cobblemon's battle engine would normally send to a player is
 * handed straight to Cobblemon's registered client handler for that packet
 * ({@link CobblemonNetwork#getS2cPayloads()}), exactly as if it had arrived
 * over the network. For the remote player the same packet is serialized with
 * its own Cobblemon codec ({@link #encode}/{@link #decode}) and relayed.
 */
public final class CobblemonPackets {

	private static final Logger LOG = LoggerFactory.getLogger(CobblemonPackets.class);
	private static Map<ResourceLocation, PacketRegisterInfo<?>> s2cById;
	private static Map<ResourceLocation, PacketRegisterInfo<?>> c2sById;

	private static final java.util.List<java.util.function.Consumer<NetworkPacket<?>>> DELIVERY_LISTENERS = new java.util.concurrent.CopyOnWriteArrayList<>();

	private CobblemonPackets() {
	}

	/** Notified on the client thread after every packet handed to Cobblemon's UI (battle visuals, turn timer). */
	public static void addDeliveryListener(java.util.function.Consumer<NetworkPacket<?>> listener) {
		DELIVERY_LISTENERS.add(listener);
	}

	/** Clientbound packet info first (battle packets for the UI), then serverbound (the guest's choices relayed to the host). */
	private static synchronized PacketRegisterInfo<?> info(ResourceLocation id) {
		if (s2cById == null) {
			s2cById = new HashMap<>();
			for (PacketRegisterInfo<?> info : CobblemonNetwork.INSTANCE.getS2cPayloads()) {
				s2cById.put(info.getId(), info);
			}
			c2sById = new HashMap<>();
			for (PacketRegisterInfo<?> info : CobblemonNetwork.INSTANCE.getC2sPayloads()) {
				c2sById.put(info.getId(), info);
			}
		}
		PacketRegisterInfo<?> clientbound = s2cById.get(id);
		return clientbound != null ? clientbound : c2sById.get(id);
	}

	/** Runs Cobblemon's client handler for {@code packet} on the client thread, as if the server had sent it. */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static void dispatchLocally(NetworkPacket<?> packet) {
		info(packet.getId());
		PacketRegisterInfo<?> info = s2cById.get(packet.getId());
		if (info == null || !(info.getHandler() instanceof ClientNetworkPacketHandler handler)) {
			LOG.warn("No Cobblemon client handler for battle packet {}", packet.getId());
			return;
		}
		Minecraft client = Minecraft.getInstance();
		client.execute(() -> {
			try {
				handler.handle((NetworkPacket) packet, client);
			} catch (Exception ex) {
				LOG.error("Cobblemon client handler failed for {}", packet.getId(), ex);
			}
			BattleVisuals.onPacket(packet);
			for (var listener : DELIVERY_LISTENERS) {
				try {
					listener.accept(packet);
				} catch (Exception ex) {
					LOG.warn("Battle packet listener failed for {}", packet.getId(), ex);
				}
			}
		});
	}

	/** Packet id + payload, encoded with Cobblemon's own codec for that packet. */
	public record Encoded(String id, byte[] payload) {
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static Encoded encode(NetworkPacket<?> packet) {
		PacketRegisterInfo info = info(packet.getId());
		if (info == null) {
			throw new IllegalArgumentException("Unknown Cobblemon packet " + packet.getId());
		}
		RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registryAccess());
		try {
			info.getCodec().encode(buffer, packet);
			byte[] bytes = new byte[buffer.readableBytes()];
			buffer.readBytes(bytes);
			return new Encoded(packet.getId().toString(), bytes);
		} finally {
			buffer.release();
		}
	}

	public static NetworkPacket<?> decode(Encoded encoded) {
		PacketRegisterInfo<?> info = info(ResourceLocation.parse(encoded.id()));
		if (info == null) {
			throw new IllegalArgumentException("Unknown Cobblemon packet " + encoded.id());
		}
		RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(encoded.payload()), registryAccess());
		try {
			return (NetworkPacket<?>) info.getDecoder().invoke(buffer);
		} finally {
			buffer.release();
		}
	}

	private static RegistryAccess registryAccess() {
		var connection = Minecraft.getInstance().getConnection();
		if (connection == null) {
			throw new IllegalStateException("Not connected to a world");
		}
		return connection.registryAccess();
	}
}
