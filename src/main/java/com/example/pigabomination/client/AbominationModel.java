package com.example.pigabomination.client;

import com.example.pigabomination.entity.AbominationAction;
import com.example.pigabomination.entity.AbominationEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;

/**
 * Modelo de la abominacion: la malla propia (una bestia a cuatro patas) con esqueleto.
 *
 * Todos los giros los calcula AbominationPose (matematica pura, que se puede probar fuera del juego). Aqui solo se
 * le pasa lo que hace la entidad y se le dan los giros a la malla, que mueve su piel con ellos:
 *  - Andar y correr: pasos cruzados con las cuatro patas, el cuerpo se bambolea y la cola se mece.
 *  - Ataques (mordisco, arañazo, embestida), aturdimiento, rugido y surgir: acciones sincronizadas con el servidor.
 */
public class AbominationModel extends EntityModel<AbominationEntity> {
    private final AbominationMesh mesh = AbominationMesh.get();
    /** Se llama "anim" porque "pose" es el nombre de la PoseStack en renderToBuffer. */
    private final AbominationPose anim = new AbominationPose();
    /** Hueso del modelo que corresponde a cada hueso de AbominationPose. */
    private final int[] boneMap = new int[AbominationPose.BONES.length];
    /** Los giros de AbominationPose, reordenados al orden de huesos del modelo. */
    private final float[][] meshRot;
    private float partialTick;

    public AbominationModel() {
        super(RenderType::entityCutoutNoCull);
        this.meshRot = new float[mesh.boneCount()][3];
        for (int i = 0; i < AbominationPose.BONES.length; i++) {
            int index = mesh.boneIndex(AbominationPose.BONES[i]);
            if (index < 0) {
                throw new IllegalStateException("El modelo no tiene el hueso " + AbominationPose.BONES[i]
                        + ": hay que regenerar abomination_mesh.json con tools/bbmodel_to_mesh.py");
            }
            this.boneMap[i] = index;
        }
    }

    @Override
    public void prepareMobModel(AbominationEntity entity, float limbSwing, float limbSwingAmount, float partialTick) {
        this.partialTick = partialTick;
    }

    @Override
    public void setupAnim(AbominationEntity entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        AbominationAction action = entity.getAction();
        float t = entity.getActionTicks(this.partialTick);
        // Al recibir un golpe se echa un momento hacia atras (hurtTime cuenta de 10 a 0).
        float hurt = entity.hurtTime > 0 ? Mth.sin(entity.hurtTime / 10.0F * Mth.PI) : 0.0F;
        this.anim.update(action, t, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch,
                entity.isSprinting(), hurt);
        for (int i = 0; i < this.boneMap.length; i++) {
            float[] src = this.anim.rot[i];
            float[] dst = this.meshRot[this.boneMap[i]];
            dst[0] = src[0];
            dst[1] = src[1];
            dst[2] = src[2];
        }
    }

    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer vc, int light, int overlay,
                               float r, float g, float b, float a) {
        pose.pushPose();
        // Deshace el giro que vanilla aplica a los modelos vivos (escala -1 y desplazamiento de 1,501),
        // para trabajar directamente con alturas en bloques.
        pose.translate(0.0D, 1.501D, 0.0D);
        pose.scale(-1.0F, -1.0F, 1.0F);
        // Agacharse, lanzarse hacia delante o saltar: se mueve todo el cuerpo (la malla lo suma y no deja que se
        // hunda en el suelo).
        this.mesh.draw(pose, vc, light, overlay, (int) (r * 255.0F), (int) (g * 255.0F), (int) (b * 255.0F),
                (int) (a * 255.0F), this.meshRot, this.anim.offsetX, this.anim.offsetY, this.anim.offsetZ);
        pose.popPose();
    }
}
