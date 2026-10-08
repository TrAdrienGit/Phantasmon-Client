package com.mystaria.phantasmon.client.admin;

import com.mystaria.phantasmon.client.auth.AuthSession;

/**
 * Whether the logged-in player is an admin (TODO-25): asked to the backend after each login. Only shows or hides the
 * admin commands — the backend checks every admin request itself.
 */
public final class AdminSession {

	private static volatile boolean admin;

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
