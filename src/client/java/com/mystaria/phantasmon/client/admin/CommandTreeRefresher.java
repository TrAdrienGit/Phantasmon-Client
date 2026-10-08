package com.mystaria.phantasmon.client.admin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundCommandsPacket;

/**
 * Fabric copies the client commands into the chat's completion tree only when the server sends its command tree,
 * keeping those whose {@code requires} holds at that moment. A player who logs in to the backend mid-game
 * ({@code /phantasmon login}) becomes an admin after that copy: the admin commands stayed hidden. {@link #refresh}
 * replays the last command tree so the copy is made again (also hides them after a logout).
 */
public final class CommandTreeRefresher {

	private static final Logger LOG = LoggerFactory.getLogger(CommandTreeRefresher.class);
	private static volatile ClientboundCommandsPacket last;

	private CommandTreeRefresher() {
	}

	/** {@code ClientPacketListenerCommandsMixin}: the server's latest command tree. */
	public static void remember(ClientboundCommandsPacket packet) {
		last = packet;
	}

	/** On the client thread: rebuilds the completion tree from the last command tree, if connected. */
	public static void refresh() {
		Minecraft.getInstance().execute(() -> {
			ClientPacketListener connection = Minecraft.getInstance().getConnection();
			ClientboundCommandsPacket packet = last;
			if (connection == null || packet == null) {
				return;
			}
			try {
				connection.handleCommands(packet);
			} catch (RuntimeException ex) {
				LOG.warn("Could not refresh the command tree", ex);
			}
		});
	}

	/** World left: the tree belonged to that server. */
	public static void clear() {
		last = null;
	}
}
