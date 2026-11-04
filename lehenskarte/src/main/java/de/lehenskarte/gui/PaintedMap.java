package de.lehenskarte.gui;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import de.lehenskarte.Config;
import de.lehenskarte.LehenskarteClient;
import de.lehenskarte.data.LehenState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;

import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Zeigt in noch nicht erkundeten (schwarzen) Bereichen von Xaero's World Map die gemalte Karte von
 * karte.ottonien.com, damit man die Umrisse der Insel schon sieht. Wird direkt nach Xaeros Kacheln und
 * vor den Kartenelementen gezeichnet: Ein Shader vergleicht mit einer Kopie des Bildschirms und malt nur
 * dort, wo Xaero nichts gezeichnet hat.
 */
public final class PaintedMap {
	private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("lehenskarte", "textures/map/karte.png");
	/** Weltkoordinaten der Bildränder (aus der Website-Karte, an den Lehensgrenzen ausgerichtet). */
	private static final double MIN_X = -5093.79, MAX_X = 5039.55, MIN_Z = -7098.98, MAX_Z = 7112.98;

	private static RenderPipeline pipeline;
	private static GpuSampler mapSampler, copySampler;
	private static GpuTexture copy;
	private static GpuTextureView copyView;
	private static boolean failed;

	private PaintedMap() {
	}

	/** cameraX/Z = Kartenmitte in Blöcken, scale = echte Pixel pro Block. */
	public static void render(double cameraX, double cameraZ, double scale) {
		Minecraft mc = Minecraft.getInstance();
		if (failed || !Config.get().paintedMap || !LehenState.isActive() || mc.level == null
				|| mc.level.dimension() != Level.OVERWORLD || scale <= 0) return;
		try {
			draw(mc, cameraX, cameraZ, scale);
		} catch (Throwable t) {
			failed = true;
			LehenskarteClient.LOGGER.warn("Lehenskarte: gemalte Karte abgeschaltet: {}", t.toString());
		}
	}

	private static void draw(Minecraft mc, double camX, double camZ, double scale) {
		RenderTarget target = mc.getMainRenderTarget();
		int w = target.width, h = target.height;
		// sichtbarer Ausschnitt in Weltkoordinaten -> Bildkoordinaten (0..1)
		// Feinjustierung: im Spiel lag die gemalte Karte etwas zu weit links oben
		camX -= Config.get().paintedMapOffsetX;
		camZ -= Config.get().paintedMapOffsetZ;
		double left = camX - w / 2.0 / scale, right = camX + w / 2.0 / scale;
		double top = camZ - h / 2.0 / scale, bottom = camZ + h / 2.0 / scale;
		if (right < MIN_X - 3000 || left > MAX_X + 3000 || bottom < MIN_Z - 3000 || top > MAX_Z + 3000) return;
		float u0 = (float) ((left - MIN_X) / (MAX_X - MIN_X)), u1 = (float) ((right - MIN_X) / (MAX_X - MIN_X));
		float v0 = (float) ((top - MIN_Z) / (MAX_Z - MIN_Z)), v1 = (float) ((bottom - MIN_Z) / (MAX_Z - MIN_Z));

		GpuDevice device = RenderSystem.getDevice();
		if (pipeline == null) {
			pipeline = RenderPipeline.builder()
					.withLocation(Identifier.fromNamespaceAndPath("lehenskarte", "pipeline/painted_map"))
					.withVertexShader(Identifier.fromNamespaceAndPath("lehenskarte", "core/painted_map"))
					.withFragmentShader(Identifier.fromNamespaceAndPath("lehenskarte", "core/painted_map"))
					.withSampler("Sampler0")
					.withSampler("Sampler1")
					.withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
					.withBlend(BlendFunction.TRANSLUCENT)
					.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
					.withDepthWrite(false)
					.withCull(false)
					.build();
			mapSampler = device.createSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, FilterMode.LINEAR, FilterMode.LINEAR, 1, OptionalDouble.empty());
			copySampler = device.createSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, FilterMode.NEAREST, FilterMode.NEAREST, 1, OptionalDouble.empty());
		}
		if (copy == null || copy.getWidth(0) != w || copy.getHeight(0) != h) {
			if (copy != null) {
				copyView.close();
				copy.close();
			}
			copy = device.createTexture(() -> "lehenskarte screen copy", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
					TextureFormat.RGBA8, w, h, 1, 1);
			copyView = device.createTextureView(copy);
		}
		GpuTextureView colorTarget = RenderSystem.outputColorTextureOverride != null
				? RenderSystem.outputColorTextureOverride : target.getColorTextureView();
		device.createCommandEncoder().copyTextureToTexture(colorTarget.texture(), copy, 0, 0, 0, 0, 0, w, h);

		AbstractTexture map = mc.getTextureManager().getTexture(TEXTURE);

		// nachts dunkler, wie das Gelände
		float day = 1f - mc.level.getSkyDarken() / 11f;
		float b = 0.88f * (0.45f + 0.55f * Math.max(0f, Math.min(1f, day)));
		BufferBuilder buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
		buf.addVertex(-1, 1, 0).setUv(u0, v0).setColor(b, b, b, 1f);
		buf.addVertex(-1, -1, 0).setUv(u0, v1).setColor(b, b, b, 1f);
		buf.addVertex(1, -1, 0).setUv(u1, v1).setColor(b, b, b, 1f);
		buf.addVertex(1, 1, 0).setUv(u1, v0).setColor(b, b, b, 1f);
		try (MeshData mesh = buf.buildOrThrow()) {
			RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
			int count = mesh.drawState().indexCount();
			GpuBuffer indexBuffer = indices.getBuffer(count);
			GpuBuffer vertexBuffer = DefaultVertexFormat.POSITION_TEX_COLOR.uploadImmediateVertexBuffer(mesh.vertexBuffer());
			try (RenderPass pass = device.createCommandEncoder().createRenderPass(() -> "lehenskarte painted map", colorTarget, OptionalInt.empty())) {
				pass.setPipeline(pipeline);
				pass.bindTexture("Sampler0", map.getTextureView(), mapSampler);
				pass.bindTexture("Sampler1", copyView, copySampler);
				pass.setIndexBuffer(indexBuffer, indices.type());
				pass.setVertexBuffer(0, vertexBuffer);
				pass.drawIndexed(0, 0, count, 1);
			}
		}
	}
}
