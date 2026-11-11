package com.mystaria.phantasmon.client.api;

import java.util.List;
import java.util.function.Consumer;

import net.minecraft.network.chat.Component;

/**
 * What the Phantasmon menu gives an add-on's page ({@link PhantasmonApi#registerMenuPage}): its footer status line and
 * its dialogs, in the menu's look. Client thread.
 */
public interface MenuHost {

	/** The footer line; {@code error}: in the danger colour. An empty message clears it. */
	void status(Component message, boolean error);

	/** A question with Cancel and {@code confirmLabel} (red when {@code danger}): {@code onConfirm} on confirmation. */
	void confirm(Component title, List<Component> lines, Component confirmLabel, boolean danger, Runnable onConfirm);

	/**
	 * A small form: one text field per label, {@code values} their initial texts (or empty). {@code onConfirm} gets the
	 * typed values, in order; the dialog stays open, busy, until {@link #closeDialog} (or {@link #dialogError}).
	 */
	void ask(Component title, List<Component> labels, List<String> values, Component confirmLabel, Consumer<List<String>> onConfirm);

	/** The open dialog failed: its error line, and it can be used again. */
	void dialogError(Component message);

	void closeDialog();

	/** Whether a dialog is open over the page (the page then gets no input). */
	boolean dialogOpen();

	/** Closes the whole menu. */
	void closeMenu();
}
