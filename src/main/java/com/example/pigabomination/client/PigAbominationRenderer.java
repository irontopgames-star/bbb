package com.example.pigabomination.client;

import com.example.pigabomination.entity.PigAbominationEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PigModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * El cerdo del encuentro (modelo de cerdo de Minecraft con una textura gris de ojos rojos).
 * Durante la transformacion tiembla cada vez mas fuerte y se hincha hasta casi un tercio mas grande.
 */
public class PigAbominationRenderer extends MobRenderer<PigAbominationEntity, PigModel<PigAbominationEntity>> {
    /** Textura propia de marcador (gris rosado con ojos rojos), para no confundirla con un cerdo normal. */
    private static final ResourceLocation TEXTURE =
            new ResourceLocation("pigabomination", "textures/entity/pig_abomination.png");
    /** Cuanto crece al final de la transformacion (0,35 = un 35 % mas grande). */
    private static final float GROWTH = 0.35F;

    public PigAbominationRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new PigModel<PigAbominationEntity>(ctx.bakeLayer(ModelLayers.PIG)), 0.7F);
    }

    @Override
    public ResourceLocation getTextureLocation(PigAbominationEntity entity) {
        return TEXTURE;
    }

    /** El temblor: pequeñas sacudidas al principio que se vuelven espasmos al final. */
    @Override
    protected void setupRotations(PigAbominationEntity entity, PoseStack pose, float ageInTicks, float rotationYaw,
                                  float partialTicks) {
        super.setupRotations(entity, pose, ageInTicks, rotationYaw, partialTicks);
        if (!entity.isTransforming()) {
            return;
        }
        float p = entity.getTransformProgress(partialTicks);
        // Los primeros 20 ticks (20 %) son de silencio e inmovilidad: el temblor empieza despues.
        float shake = Mth.clamp((p - 0.2F) / 0.8F, 0.0F, 1.0F);
        float amp = 0.01F + 0.09F * shake * shake;
        float t = ageInTicks;
        pose.translate(Mth.sin(t * 2.9F) * amp, Math.abs(Mth.sin(t * 3.7F)) * amp * 0.6F, Mth.cos(t * 3.3F) * amp);
    }

    /** El cuerpo crece despacio al principio y de golpe al final. */
    @Override
    protected void scale(PigAbominationEntity entity, PoseStack pose, float partialTicks) {
        if (!entity.isTransforming()) {
            return;
        }
        float p = entity.getTransformProgress(partialTicks);
        float s = 1.0F + GROWTH * p * p;
        pose.scale(s, s, s);
    }
}
