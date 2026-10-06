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
		admin = false;
		if (!session.isAuthenticated()) {
			return;
		}
		client.me(session.accessToken())
				.thenAccept(me -> admin = me != null && me.admin())
				.exceptionally(ex -> {
					admin = false;
					return null;
				});
	}

	public static void clear() {
		admin = false;
	}
}
