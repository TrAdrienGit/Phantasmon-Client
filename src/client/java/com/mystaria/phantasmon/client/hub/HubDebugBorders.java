package com.mystaria.phantasmon.client.hub;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;

import org.joml.Matrix4f;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.Util;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * Admin debug of the hubs (Adrien 2026-10-29): the six faces of a hub's box drawn with the world border's force field,
 * for every anchor of that hub on this server, so its limits are plain to see. A toggle per hub, on this client only
 * ({@code /phantasmon admin hub debug <hub>}), forgotten when the game closes.
 */
public final class HubDebugBorders {

	private static final ResourceLocation FORCEFIELD = ResourceLocation.withDefaultNamespace("textures/misc/forcefield.png");
	private static final Set<String> HUBS = new HashSet<>();

	private HubDebugBorders() {
	}

	/** Shows or hides that hub's borders; true if now shown. */
	public static boolean toggle(String hub) {
		String key = hub.toLowerCase(Locale.ROOT);
		if (HUBS.remove(key)) {
			return false;
		}
		HUBS.add(key);
		return true;
	}

	/** {@code WorldRenderEvents.AFTER_TRANSLUCENT}. */
	public static void render(WorldRenderContext context, Supplier<List<HubAnchorDto>> anchors) {
		if (HUBS.isEmpty()) {
			return;
		}
		Vec3 camera = context.camera().getPosition();
		Matrix4f pose = context.matrixStack() == null ? new Matrix4f() : context.matrixStack().last().pose();
		BufferBuilder buffer = null;
		float scroll = (Util.getMillis() % 3000L) / 3000.0F;
		for (HubAnchorDto anchor : anchors.get()) {
			if (!HUBS.contains(anchor.hub().toLowerCase(Locale.ROOT))) {
				continue;
			}
			if (buffer == null) {
				buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
			}
			HubBuildLayout layout = anchor.layout();
			int[] min = layout.min();
			int[] max = layout.max();
			box(buffer, pose, camera, min[0], min[1], min[2], max[0] + 1, max[1] + 1, max[2] + 1, scroll);
		}
		if (buffer == null) {
			return;
		}
		MeshData mesh = buffer.build();
		if (mesh == null) {
			return;
		}
		RenderSystem.enableBlend();
		RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE,
				GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
		RenderSystem.setShaderTexture(0, FORCEFIELD);
		RenderSystem.setShader(GameRenderer::getPositionTexShader);
		RenderSystem.setShaderColor(0.75F, 0.35F, 1.0F, 0.8F);
		RenderSystem.depthMask(false);
		RenderSystem.polygonOffset(-3.0F, -3.0F);
		RenderSystem.enablePolygonOffset();
		RenderSystem.disableCull();
		BufferUploader.drawWithShader(mesh);
		RenderSystem.enableCull();
		RenderSystem.polygonOffset(0.0F, 0.0F);
		RenderSystem.disablePolygonOffset();
		RenderSystem.depthMask(true);
		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableBlend();
	}

	/** The six faces of the box, the texture repeating every 2 blocks and scrolling like the world border. */
	private static void box(BufferBuilder buffer, Matrix4f pose, Vec3 camera, double x0, double y0, double z0, double x1,
			double y1, double z1, float scroll) {
		float ax = (float) (x0 - camera.x), bx = (float) (x1 - camera.x);
		float ay = (float) (y0 - camera.y), by = (float) (y1 - camera.y);
		float az = (float) (z0 - camera.z), bz = (float) (z1 - camera.z);
		// North and south (u along x, v along y)
		quad(buffer, pose, ax, ay, az, bx, ay, az, bx, by, az, ax, by, az, x0, x1, y0, y1, scroll);
		quad(buffer, pose, ax, ay, bz, bx, ay, bz, bx, by, bz, ax, by, bz, x0, x1, y0, y1, scroll);
		// West and east (u along z, v along y)
		quad(buffer, pose, ax, ay, az, ax, ay, bz, ax, by, bz, ax, by, az, z0, z1, y0, y1, scroll);
		quad(buffer, pose, bx, ay, az, bx, ay, bz, bx, by, bz, bx, by, az, z0, z1, y0, y1, scroll);
		// Bottom and top (u along x, v along z)
		quad(buffer, pose, ax, ay, az, bx, ay, az, bx, ay, bz, ax, ay, bz, x0, x1, z0, z1, scroll);
		quad(buffer, pose, ax, by, az, bx, by, az, bx, by, bz, ax, by, bz, x0, x1, z0, z1, scroll);
	}

	private static void quad(BufferBuilder buffer, Matrix4f pose, float x0, float y0, float z0, float x1, float y1, float z1,
			float x2, float y2, float z2, float x3, float y3, float z3, double u0, double u1, double v0, double v1, float scroll) {
		float a = (float) (u0 / 2) + scroll, b = (float) (u1 / 2) + scroll;
		float c = (float) (v0 / 2), d = (float) (v1 / 2);
		buffer.addVertex(pose, x0, y0, z0).setUv(a, d);
		buffer.addVertex(pose, x1, y1, z1).setUv(b, d);
		buffer.addVertex(pose, x2, y2, z2).setUv(b, c);
		buffer.addVertex(pose, x3, y3, z3).setUv(a, c);
	}
}
