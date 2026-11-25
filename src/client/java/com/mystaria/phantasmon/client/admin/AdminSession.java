package com.mystaria.phantasmon.client.admin;

import com.mystaria.phantasmon.client.auth.AuthSession;

/**
 * Whether the logged-in player is an admin (TODO-25): asked to the backend after each login. Only shows or hides the
 * admin commands — the backend checks every admin request itself.
 */
public final class AdminSession {

	private static volatile boolean admin;
	private static AdminClient boundClient;
	private static AuthSession boundSession;

	private AdminSession() {
	}

	public static boolean isAdmin() {
		return admin;
	}

	public static void refresh(AdminClient client, AuthSession session) {
		set(false);
		if (!session.isAuthenticated()) {
			return;
		}
		client.me(session.accessToken())
				.thenAccept(me -> set(me != null && me.admin()))
				.exceptionally(ex -> {
					set(false);
					return null;
				});
	}

	/** Phantasmon's client gives what {@link #refreshNow} needs, once, at startup. */
	public static void bind(AdminClient client, AuthSession session) {
		boundClient = client;
		boundSession = session;
	}

	/**
	 * The backend says this player was made admin or removed ({@code AdminChanged}, 2026-11-25): asked again, without
	 * dropping the current rights meanwhile; the menu and the commands follow the answer, no reconnection.
	 */
	public static void refreshNow() {
		if (boundClient == null || boundSession == null || !boundSession.isAuthenticated()) {
			return;
		}
		boundClient.me(boundSession.accessToken()).thenAccept(me -> set(me != null && me.admin()));
	}

	public static void clear() {
		set(false);
	}

	/** The admin commands appear or disappear in the chat's completion as soon as the answer changes. */
	private static void set(boolean value) {
		boolean changed = admin != value;
		admin = value;
		if (changed) {
			CommandTreeRefresher.refresh();
		}
	}
}
