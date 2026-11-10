package com.mystaria.phantasmon.client.api;

import java.util.List;
import java.util.function.Consumer;

import net.minecraft.network.chat.Component;

import com.mystaria.phantasmon.client.hub.HubAdminDto;

/**
 * A tab an add-on adds to the hub manager, for admins only: players see Phantasmon's classic hubs and nothing else
 * (Adrien 2026-11-10: no add-on hub, no add-on anchor for them). A tab an add-on adds to the admins' hub manager (Phantasmon Events: its event hubs, mirror hubs among them),
 * registered with {@link PhantasmonApi#registerHubManagerTab}. Phantasmon draws the tab like its own "Hubs" tab: the
 * list of the hubs it {@link #shows}, the selected hub's details, its anchors (deletable one by one), rename and
 * delete; the tab adds its own lines and buttons.
 */
public interface HubManagerTab {

	/** The tab's label. */
	Component title();

	/** Whether this tab lists {@code hub} (for instance its kind is {@code MIRROR}). */
	boolean shows(HubAdminDto hub);

	/** Extra lines under the selected hub's details (radius, terrain...). */
	default List<Component> details(HubAdminDto hub) {
		return List.of();
	}

	/** Extra buttons for the selected hub (resize...). */
	default List<Action> actions() {
		return List.of();
	}

	/** The tab's own "create" button (admins); null: the tab creates nothing from the manager. */
	default Action createAction() {
		return null;
	}

	/**
	 * A button: {@code run} receives the selected hub (null for {@link #createAction}) and a {@code done} callback that
	 * reloads the manager's lists (call it on the client thread once the backend answered).
	 */
	record Action(Component label, boolean danger, Run run) {
	}

	@FunctionalInterface
	interface Run {
		void run(HubAdminDto hub, Runnable done, Consumer<Component> status);
	}
}
