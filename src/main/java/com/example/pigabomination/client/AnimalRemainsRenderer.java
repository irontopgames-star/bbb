package com.example.pigabomination.client;

import com.example.pigabomination.entity.AnimalRemainsEntity;
import com.example.pigabomination.entity.PreyKind;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * Dibuja los restos con el modelo y la textura de vanilla del animal, para que se reconozcan.
 * El cadaver va tumbado de costado y su borde toca el suelo; la cabeza se apoya en el suelo.
 * Las moscas son sprites que giran alrededor del cadaver.
 *
 * Sistema de coordenadas: el modelo vanilla se dibuja con los pies a 1,501 bloques (FEET_OFFSET) y
 * girado (scale -1), asi que una pieza situada a y pixeles del origen queda a (1,501 - y/16) bloques del suelo.
 */
public abstract class AnimalRemainsRenderer<T extends AnimalRemainsEntity> extends EntityRenderer<T> {
    /** Altura del centro del cuerpo para el giro al tumbarlo, en bloques. Cualquier valor sirve: se compensa. */
    private static final float BODY_CENTER = 0.9F;
    /** Medio alto de la cabeza (cubo de 8 pixeles): su centro queda a esta altura del suelo. */
    private static final float HEAD_HALF = 0.25F;
    /** Correccion de los modelos vivos de vanilla: los pies quedan a 1,501 bloques del origen del modelo. */
    private static final float FEET_OFFSET = 1.501F;
    private static final int FLIES = 3;
    private static final ResourceLocation FLY_TEXTURE =
            new ResourceLocation("pigabomination", "textures/entity/fly.png");

    private final ModelPart[] models = new ModelPart[PreyKind.values().length];
    private final boolean lying;

    protected AnimalRemainsRenderer(EntityRendererProvider.Context ctx, boolean lying) {
        super(ctx);
        this.lying = lying;
        this.shadowRadius = 0.3F;
        this.models[PreyKind.COW.ordinal()] = ctx.bakeLayer(ModelLayers.COW);
        this.models[PreyKind.SHEEP.ordinal()] = ctx.bakeLayer(ModelLayers.SHEEP);
        this.models[PreyKind.CHICKEN.ordinal()] = ctx.bakeLayer(ModelLayers.CHICKEN);
        this.models[PreyKind.PIG.ordinal()] = ctx.bakeLayer(ModelLayers.PIG);
    }

    @Override
    public void render(T entity, float entityYaw, float partialTicks, PoseStack pose,
                       MultiBufferSource buffer, int packedLight) {
        PreyKind kind = entity.getKind();
        ModelPart root = this.models[kind.ordinal()];
        ModelPart part = this.lying ? root : root.getChild("head");

        if (entity.hasFlies()) {
            drawFlies(entity, partialTicks, pose, buffer, packedLight);
        }

        pose.pushPose();
        if (this.lying) {
            // Gira alrededor de BODY_CENTER y deja el borde del cuerpo (lado a lado) apoyado en el suelo.
            pose.translate(0.0D, -(BODY_CENTER - layHalf(kind)), 0.0D);
            pose.mulPose(Axis.YP.rotationDegrees(180.0F - entity.getYRot()));
            pose.translate(0.0D, BODY_CENTER, 0.0D);
            pose.mulPose(Axis.ZP.rotationDegrees(90.0F));
            pose.translate(0.0D, -BODY_CENTER, 0.0D);
        } else {
            // Altura real de la cabeza sobre el suelo, leida del propio modelo: la bajamos hasta apoyarla.
            float pivotHeight = FEET_OFFSET - part.y / 16.0F;
            pose.translate(0.0D, -(pivotHeight - HEAD_HALF), 0.0D);
            pose.mulPose(Axis.YP.rotationDegrees(180.0F - entity.getYRot()));
        }
        pose.scale(-1.0F, -1.0F, 1.0F);
        pose.translate(0.0D, -FEET_OFFSET, 0.0D);
        part.render(pose, buffer.getBuffer(RenderType.entityCutoutNoCull(texture(kind))),
                packedLight, OverlayTexture.NO_OVERLAY);
        pose.popPose();

        super.render(entity, entityYaw, partialTicks, pose, buffer, packedLight);
    }

    /** Tres moscas que giran alrededor del cadaver, siempre de cara a la camara. */
    private void drawFlies(T entity, float partialTicks, PoseStack pose, MultiBufferSource buffer, int packedLight) {
        VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(FLY_TEXTURE));
        float t = entity.tickCount + partialTicks;
        for (int i = 0; i < FLIES; i++) {
            float angle = t * 0.25F + i * (Mth.TWO_PI / FLIES);
            float radius = 0.7F + 0.1F * Mth.sin(t * 0.2F + i);
            float x = Mth.cos(angle) * radius;
            float z = Mth.sin(angle) * radius;
            float y = 0.45F + 0.08F * Mth.sin(t * 0.6F + i * 2.0F);
            pose.pushPose();
            pose.translate(x, y, z);
            pose.mulPose(this.entityRenderDispatcher.cameraOrientation());
            Matrix4f m = pose.last().pose();
            float s = 0.06F;
            flyVertex(vc, m, -s, -s, 0.0F, 1.0F, packedLight);
            flyVertex(vc, m, s, -s, 1.0F, 1.0F, packedLight);
            flyVertex(vc, m, s, s, 1.0F, 0.0F, packedLight);
            flyVertex(vc, m, -s, s, 0.0F, 0.0F, packedLight);
            pose.popPose();
        }
    }

    private static void flyVertex(VertexConsumer vc, Matrix4f m, float x, float y, float u, float v, int light) {
        vc.vertex(m, x, y, 0.0F)
                .color(255, 255, 255, 255)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light)
                .normal(0.0F, 1.0F, 0.0F)
                .endVertex();
    }

    /** Medio ancho del cuerpo de cada animal (modelo vanilla): es el borde que toca el suelo al tumbarlo. */
    private static float layHalf(PreyKind kind) {
        return switch (kind) {
            case COW -> 0.375F;
            case SHEEP -> 0.25F;
            case CHICKEN -> 0.1875F;
            case PIG -> 0.3125F;
        };
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return texture(entity.getKind());
    }

    /** Textura de vanilla del animal: se usa desde el juego, no se copia al mod. */
    private static ResourceLocation texture(PreyKind kind) {
        return switch (kind) {
            case COW -> new ResourceLocation("minecraft", "textures/entity/cow/cow.png");
            case SHEEP -> new ResourceLocation("minecraft", "textures/entity/sheep/sheep.png");
            case CHICKEN -> new ResourceLocation("minecraft", "textures/entity/chicken.png");
            case PIG -> new ResourceLocation("minecraft", "textures/entity/pig/pig.png");
        };
    }
}
