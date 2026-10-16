package fr.minenorth.pets.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.minenorth.pets.MineNorthPets;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * Harnais du chien K9 : deux sangles bleues autour du corps, une poignée sur le dos et un badge jaune sur chaque flanc, dessinés à partir
 * de la boîte du cube du corps (identique pour tous les modèles de Polly Pets, qui n'ont pas la même texture ni les mêmes UV).
 */
public class K9HarnessLayer<T extends GeoAnimatable> extends GeoRenderLayer<T> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(MineNorthPets.MOD_ID, "textures/entity/k9_harness.png");
    /** Texture 4x4 : moitié gauche bleue, moitié droite jaune. */
    private static final float BLUE_U = 0.125f, YELLOW_U = 0.875f, V = 0.5f;
    private static final float GROW = 0.03f;
    private static boolean logged;

    public K9HarnessLayer(GeoRenderer<T> renderer) { super(renderer); }

    private static boolean isBody(GeoBone bone) {
        for (GeoBone c : bone.getChildBones()) if (c.getName().startsWith("head")) return true;
        return false;
    }

    @Override
    public void renderForBone(PoseStack ps, T animatable, GeoBone bone, RenderType renderType, MultiBufferSource buffers, VertexConsumer buffer,
                              float partialTick, int light, int overlay) {
        if (!(animatable instanceof Entity e) || !ClientK9.has(e.getId()) || bone.isHidden() || !isBody(bone) || bone.getCubes().isEmpty()) return;
        if (!logged) {
            logged = true;
            PetRenderHooks.LOGGER.info("[MineNorthPets] harnais K9 dessiné sur l'os {} de l'entité {}", bone.getName(), e.getType());
        }
        float x0 = Float.MAX_VALUE, y0 = Float.MAX_VALUE, z0 = Float.MAX_VALUE, x1 = -Float.MAX_VALUE, y1 = -Float.MAX_VALUE, z1 = -Float.MAX_VALUE;
        GeoCube cube = bone.getCubes().get(0);
        for (GeoQuad q : cube.quads()) {
            for (GeoVertex v : q.vertices()) {
                x0 = Math.min(x0, v.position().x); x1 = Math.max(x1, v.position().x);
                y0 = Math.min(y0, v.position().y); y1 = Math.max(y1, v.position().y);
                z0 = Math.min(z0, v.position().z); z1 = Math.max(z1, v.position().z);
            }
        }
        float len = z1 - z0, h = y1 - y0;
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        // Le modèle regarde vers -Z : z0 est l'avant.
        box(vc, ps, x0 - GROW, y0 - GROW, z0 + len * 0.28f, x1 + GROW, y1 + GROW, z0 + len * 0.40f, BLUE_U, light, overlay);
        box(vc, ps, x0 - GROW, y0 - GROW, z0 + len * 0.62f, x1 + GROW, y1 + GROW, z0 + len * 0.72f, BLUE_U, light, overlay);
        float cx = (x0 + x1) / 2f;
        box(vc, ps, cx - 0.04f, y1, z0 + len * 0.30f, cx + 0.04f, y1 + GROW + 0.035f, z0 + len * 0.70f, BLUE_U, light, overlay);
        float by = y0 + h * 0.45f, bh = h * 0.30f, bz = z0 + len * 0.31f;
        box(vc, ps, x0 - GROW - 0.01f, by, bz, x0 - GROW + 0.005f, by + bh, bz + len * 0.08f, YELLOW_U, light, overlay);
        box(vc, ps, x1 + GROW - 0.005f, by, bz, x1 + GROW + 0.01f, by + bh, bz + len * 0.08f, YELLOW_U, light, overlay);
    }

    private static void box(VertexConsumer vc, PoseStack ps, float x0, float y0, float z0, float x1, float y1, float z1, float u, int light, int overlay) {
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        quad(vc, m, n, light, overlay, u, 0, 0, -1, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0);
        quad(vc, m, n, light, overlay, u, 0, 0, 1, x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1);
        quad(vc, m, n, light, overlay, u, -1, 0, 0, x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1);
        quad(vc, m, n, light, overlay, u, 1, 0, 0, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0);
        quad(vc, m, n, light, overlay, u, 0, 1, 0, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1);
        quad(vc, m, n, light, overlay, u, 0, -1, 0, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0);
    }

    private static void quad(VertexConsumer vc, Matrix4f m, Matrix3f n, int light, int overlay, float u, float nx, float ny, float nz,
                             float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz) {
        vertex(vc, m, n, light, overlay, u, nx, ny, nz, ax, ay, az);
        vertex(vc, m, n, light, overlay, u, nx, ny, nz, bx, by, bz);
        vertex(vc, m, n, light, overlay, u, nx, ny, nz, cx, cy, cz);
        vertex(vc, m, n, light, overlay, u, nx, ny, nz, dx, dy, dz);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f n, int light, int overlay, float u, float nx, float ny, float nz,
                               float x, float y, float z) {
        vc.vertex(m, x, y, z).color(255, 255, 255, 255).uv(u, V).overlayCoords(overlay).uv2(light).normal(n, nx, ny, nz).endVertex();
    }
}
