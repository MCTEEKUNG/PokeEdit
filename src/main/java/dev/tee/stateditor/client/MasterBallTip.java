package dev.tee.stateditor.client;

import dev.tee.stateditor.StatEditorMod;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/** The shell and its raised latch share one textured render pass. The front faces positive Z. */
final class MasterBallTip {
    private static final int SLICES = 24;
    private static final int RINGS = 16;
    private static final RenderLayer LAYER = RenderLayer.getEntitySolid(
            Identifier.of(StatEditorMod.MOD_ID, "textures/item/pokeedit_master_ball.png"));
    private static final Point[][] SHELL = makeShell();
    private static final int LATCH_SIDES = 16;
    private static final float[][] LATCH = makeLatch();

    private MasterBallTip() {}

    static void render(MatrixStack.Entry pose, VertexConsumerProvider consumers, int light, int overlay) {
        VertexConsumer vertices = consumers.getBuffer(LAYER);
        for (int row = 0; row < RINGS; row++) {
            for (int col = 0; col < SLICES; col++) {
                SHELL[row][col].draw(vertices, pose, light, overlay);
                SHELL[row + 1][col].draw(vertices, pose, light, overlay);
                SHELL[row + 1][col + 1].draw(vertices, pose, light, overlay);
                SHELL[row][col + 1].draw(vertices, pose, light, overlay);
            }
        }
        // Three bevels rise from the shell. Sample a plain ivory texel for the tinted metal.
        for (int i = 0; i < LATCH_SIDES; i++) {
            bevel(vertices, pose, light, overlay, i, .255f, .96f, .235f, 1.016f, 0xFF382F41);
            bevel(vertices, pose, light, overlay, i, .235f, 1.016f, .168f, 1.039f, 0xFF4E4658);
            bevel(vertices, pose, light, overlay, i, .168f, 1.039f, .132f, 1.063f, 0xFFCEC4D4);
            bevel(vertices, pose, light, overlay, i, .132f, 1.063f, 0f, 1.065f, 0xFFFFFFFF);
        }
    }

    private static Point[][] makeShell() {
        Point[][] points = new Point[RINGS + 1][SLICES + 1];
        for (int row = 0; row <= RINGS; row++) {
            float v = (float) row / RINGS;
            double latitude = (.5 - v) * Math.PI;
            for (int col = 0; col <= SLICES; col++) {
                float u = (float) col / SLICES;
                double longitude = (u - .5) * 2 * Math.PI;
                float x = (float) (Math.cos(latitude) * Math.sin(longitude));
                float y = (float) Math.sin(latitude);
                float z = (float) (Math.cos(latitude) * Math.cos(longitude));
                points[row][col] = new Point(x, y, z, u, v);
            }
        }
        return points;
    }

    private record Point(float x, float y, float z, float u, float v) {
        void draw(VertexConsumer buffer, MatrixStack.Entry pose, int light, int overlay) {
            buffer.vertex(pose, x, y, z).color(-1).texture(u, v).overlay(overlay).light(light).normal(pose, x, y, z);
        }
    }

    private static float[][] makeLatch() {
        float[][] circle = new float[LATCH_SIDES + 1][2];
        for (int i = 0; i <= LATCH_SIDES; i++) {
            double angle = i * 2 * Math.PI / LATCH_SIDES;
            circle[i][0] = (float) Math.cos(angle);
            circle[i][1] = (float) Math.sin(angle);
        }
        return circle;
    }

    private static void bevel(VertexConsumer buffer, MatrixStack.Entry pose, int light, int overlay, int i,
                              float outer, float back, float inner, float front, int color) {
        rimVertex(buffer, pose, light, overlay, LATCH[i], outer, back, color);
        rimVertex(buffer, pose, light, overlay, LATCH[i + 1], outer, back, color);
        rimVertex(buffer, pose, light, overlay, LATCH[i + 1], inner, front, color);
        rimVertex(buffer, pose, light, overlay, LATCH[i], inner, front, color);
    }

    private static void rimVertex(VertexConsumer buffer, MatrixStack.Entry pose, int light, int overlay,
                                  float[] unit, float radius, float z, int color) {
        buffer.vertex(pose, radius * unit[0], radius * unit[1], z).color(color).texture(.5f, .56f)
                .overlay(overlay).light(light).normal(pose, 0, 0, 1);
    }
}
