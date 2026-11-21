package dev.tee.stateditor.client;

import com.cobblemon.mod.common.client.gui.PokemonGuiUtilsKt;
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState;
import com.cobblemon.mod.common.client.render.models.blockbench.repository.RenderContext;
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository;
import com.cobblemon.mod.common.entity.PoseType;
import com.cobblemon.mod.common.pokemon.RenderablePokemon;
import com.cobblemon.mod.common.util.math.QuaternionUtilsKt;
import dev.tee.stateditor.StatEditorMod;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Fits the actual animated mesh, rather than guessing a zoom from the species' profile scale. */
final class FittedPokemonPreview {
    private RenderablePokemon pokemon;
    private FloatingState state = new FloatingState();
    private ModelBounds bounds = new ModelBounds();
    private float yaw;
    private float pitch;
    private boolean loggedError;
    private int x;
    private int y;
    private final int width;
    private final int height;
    private final int padding;

    FittedPokemonPreview(RenderablePokemon pokemon, int x, int y, int width, int height, int padding) {
        this.pokemon = pokemon;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.padding = padding;
    }

    void update(RenderablePokemon pokemon, int x, int y) {
        this.x = x;
        this.y = y;
        if (!this.pokemon.equals(pokemon)) {
            this.pokemon = pokemon;
            state = new FloatingState();
            bounds = new ModelBounds();
            yaw = pitch = 0;
            loggedError = false;
        }
    }

    void render(DrawContext context, int mouseX, int mouseY, float delta) {
        Quaternionf rotation = QuaternionUtilsKt.fromEulerXYZDegrees(new Quaternionf(), new Vector3f(13, 325, 0));
        var look = PokemonGuiUtilsKt.calculateHeadYawAndPitch(x + width / 2f, y + height / 2f,
                rotation, mouseX, mouseY, yaw, pitch, false);
        yaw = look.getFirst();
        pitch = look.getSecond();
        try {
            measure(rotation, delta);
            ModelBounds.Fit fit = bounds.fit(x, y, width, height, padding);
            if (fit == null) return;
            var matrices = context.getMatrices();
            context.enableScissor(x, y, x + width, y + height);
            matrices.push();
            try {
                matrices.translate(fit.x(), fit.y(), 0);
                matrices.scale((float) fit.scale(), (float) fit.scale(), (float) fit.scale());
                PokemonGuiUtilsKt.drawProfilePokemon(pokemon, matrices, rotation, PoseType.PROFILE,
                        state, 0f, 1f, false, false, 1f, 1f, 1f, 1f, yaw, pitch);
                context.draw();
            } finally {
                matrices.pop();
                context.disableScissor();
            }
        } catch (RuntimeException exception) {
            if (!loggedError) {
                StatEditorMod.LOGGER.warn("Could not fit Pokémon preview for {}", pokemon.getSpecies().getName(), exception);
                loggedError = true;
            }
        }
    }

    private void measure(Quaternionf rotation, float delta) {
        state.setCurrentAspects(pokemon.getAspects());
        var species = pokemon.getSpecies().getResourceIdentifier();
        var repository = VaryingModelRepository.INSTANCE;
        var model = repository.getPoser(species, state);
        RenderContext renderContext = new RenderContext();
        renderContext.put(RenderContext.Companion.getTEXTURE(), repository.getTextureNoSubstitute(species, state));
        renderContext.put(RenderContext.Companion.getSCALE(), pokemon.getForm().getBaseScale());
        renderContext.put(RenderContext.Companion.getSPECIES(), species);
        renderContext.put(RenderContext.Companion.getASPECTS(), pokemon.getAspects());
        renderContext.put(RenderContext.Companion.getRENDER_STATE(), RenderContext.RenderState.PROFILE);
        renderContext.put(RenderContext.Companion.getPOSABLE_STATE(), state);
        renderContext.put(RenderContext.Companion.getDO_QUIRKS(), false);
        model.setContext(renderContext);
        state.setCurrentModel(model);
        state.setPoseToFirstSuitable(PoseType.PROFILE);
        state.updatePartialTicks(delta);
        try {
            model.applyAnimations(null, state, 0f, 0f, 0f, yaw, pitch);
            MatrixStack matrices = new MatrixStack();
            matrices.scale(1f, 1f, -1f);
            matrices.translate(0f, 0f, -4f);
            matrices.multiply(rotation);
            // CPU-only pass: no GL reads, render targets, or modifications to the world entity.
            model.getRootPart().render(renderContext, matrices, new BoundsConsumer(bounds), 0xF000F0, 0, -1);
        } finally {
            model.setDefault();
        }
    }

    private record BoundsConsumer(ModelBounds bounds) implements VertexConsumer {
        @Override public VertexConsumer vertex(float x, float y, float z) { bounds.include(x, y); return this; }
        @Override public VertexConsumer color(int r, int g, int b, int a) { return this; }
        @Override public VertexConsumer texture(float u, float v) { return this; }
        @Override public VertexConsumer overlay(int u, int v) { return this; }
        @Override public VertexConsumer light(int u, int v) { return this; }
        @Override public VertexConsumer normal(float x, float y, float z) { return this; }
    }
}
