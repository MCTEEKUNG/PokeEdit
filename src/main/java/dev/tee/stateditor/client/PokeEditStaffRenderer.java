package dev.tee.stateditor.client;

import dev.tee.stateditor.StatEditorMod;
import dev.tee.stateditor.item.StatEditorItems;
import net.fabricmc.fabric.api.client.model.loading.v1.FabricBakedModelManager;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

public final class PokeEditStaffRenderer {
    private static final Identifier BODY = Identifier.of(StatEditorMod.MOD_ID, "item/pokeedit_staff_body");
    private static final float BALL_Y = (14.9f - 8) / 16;
    private static final float BALL_RADIUS = 3.32f / 16;

    private PokeEditStaffRenderer() {}

    public static void register() {
        ModelLoadingPlugin.register(context -> context.addModels(BODY));
        BuiltinItemRendererRegistry.INSTANCE.register(StatEditorItems.pokeEditConsole(), PokeEditStaffRenderer::render);
    }

    private static void render(ItemStack stack, ModelTransformationMode mode, MatrixStack matrices,
                               VertexConsumerProvider consumers, int light, int overlay) {
        var client = MinecraftClient.getInstance();
        var models = (FabricBakedModelManager) client.getBakedModelManager();
        matrices.push();
        try {
            matrices.translate(.5f, .5f, .5f);
            client.getItemRenderer().renderItem(stack, ModelTransformationMode.NONE, false, matrices, consumers,
                    light, overlay, models.getModel(BODY));
            matrices.translate(0, BALL_Y, 0);
            if (mode.isFirstPerson() || mode == ModelTransformationMode.THIRD_PERSON_RIGHT_HAND
                    || mode == ModelTransformationMode.THIRD_PERSON_LEFT_HAND) {
                float phase = (Util.getMeasuringTimeMs() % 4000) * (MathHelper.TAU / 4000);
                matrices.translate(0, .013f * MathHelper.sin(phase * 2), 0);
                float facing = switch (mode) {
                    case FIRST_PERSON_RIGHT_HAND -> -22;
                    case FIRST_PERSON_LEFT_HAND -> 22;
                    default -> 180;
                };
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(facing + 14 * MathHelper.sin(phase)));
            }
            matrices.scale(BALL_RADIUS, BALL_RADIUS, BALL_RADIUS);
            int shellLight = LightmapTextureManager.pack(Math.max(7, LightmapTextureManager.getBlockLightCoordinates(light)),
                    LightmapTextureManager.getSkyLightCoordinates(light));
            MasterBallTip.render(matrices.peek(), consumers, shellLight, overlay);
        } finally {
            matrices.pop();
        }
    }
}
