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
	 * A button. With {@code fields}, the manager first asks for them in a form (Phantasmon's look) and gives their
	 * values, in order, to {@code form}; without, it runs at once. {@code form} receives the selected hub (null for
	 * {@link #createAction}), the values, a {@code done} callback that reloads the manager's lists (call it once the
	 * backend answered) and a {@code status} line for the manager's footer.
	 */
	record Action(Component label, boolean danger, List<Field> fields, FormRun form) {

		/** A button without form. */
		public Action(Component label, boolean danger, Run run) {
			this(label, danger, List.of(), (hub, values, done, status) -> run.run(hub, done, status));
		}
	}

	/** One input of an action's form. */
	record Field(Component label, Kind kind) {

		public enum Kind {
			/** A hub name: lower-case letters, digits, _ (2 to 32). */
			NAME,
			/** A whole number from 0. */
			NUMBER,
			/** A whole number, possibly negative. */
			SIGNED_NUMBER,
			/** Any text (32 characters). */
			TEXT
		}
	}

	@FunctionalInterface
	interface Run {
		void run(HubAdminDto hub, Runnable done, Consumer<Component> status);
	}

	@FunctionalInterface
	interface FormRun {
		void run(HubAdminDto hub, List<String> values, Runnable done, Consumer<Component> status);
	}
}
