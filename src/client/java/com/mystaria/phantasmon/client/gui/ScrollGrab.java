package com.mystaria.phantasmon.client.gui;

/**
 * A scrollbar held with the mouse (2026-11-21): a press on the bar's track grabs the thumb (or centres it under the
 * cursor when the press is beside it), dragging moves it. Every list with a bar uses one, so none scrolls with the
 * wheel only. Geometry in the caller's own pixels; the thumb is {@code max(minThumb, track² / total)} tall, as the
 * bars are drawn.
 */
public final class ScrollGrab {
	/** How far beside the drawn bar a press still takes it: the bars are thin. */
	private static final int MARGIN = 3;

	private boolean active;
	private double grab;
	private int trackY;
	private int trackH;
	private int thumbH;
	private int maxScroll;

	/**
	 * A press at ({@code mouseX}, {@code mouseY}) on a bar {@code barW} wide at {@code barX}, whose track runs from
	 * {@code trackY} for {@code trackH}, for {@code total} of content scrolled by {@code scroll}. True when it took
	 * the bar: the caller then sets its scroll from {@link #drag} right away.
	 */
	public boolean press(double mouseX, double mouseY, int barX, int barW, int trackY, int trackH, int total, int scroll, int minThumb) {
		if (total <= trackH || trackH <= 0) {
			return false;
		}
		if (mouseX < barX - MARGIN || mouseX >= barX + barW + MARGIN || mouseY < trackY || mouseY >= trackY + trackH) {
			return false;
		}
		this.trackY = trackY;
		this.trackH = trackH;
		this.maxScroll = total - trackH;
		this.thumbH = Math.min(trackH, Math.max(minThumb, trackH * trackH / total));
		int thumbTop = trackY + Math.round((trackH - thumbH) * (scroll / (float) maxScroll));
		boolean onThumb = mouseY >= thumbTop && mouseY < thumbTop + thumbH;
		grab = onThumb ? mouseY - thumbTop : thumbH / 2.0;
		active = true;
		return true;
	}

	/** The scroll the thumb held at {@code mouseY} stands for, from 0 to {@code total - track}. */
	public int drag(double mouseY) {
		int free = trackH - thumbH;
		float t = free <= 0 ? 0 : (float) ((mouseY - grab - trackY) / free);
		return Math.max(0, Math.min(maxScroll, Math.round(t * maxScroll)));
	}

	/** The same, for a list scrolled by rows of {@code rowH}: the row offset. */
	public int dragRows(double mouseY, int rowH) {
		return Math.round(drag(mouseY) / (float) rowH);
	}

	public boolean active() {
		return active;
	}

	public void release() {
		active = false;
	}
}
