package com.example.pigabomination.client;

import com.example.pigabomination.PigAbominationMod;
import com.example.pigabomination.entity.AbominationEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * La abominacion (noches y dia 5): malla propia con esqueleto, una bestia a cuatro patas (ver AbominationModel).
 */
public class AbominationRenderer extends MobRenderer<AbominationEntity, AbominationModel> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(PigAbominationMod.MODID, "textures/entity/abomination_mesh.png");

    public AbominationRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new AbominationModel(), 1.0F);
    }

    @Override
    public ResourceLocation getTextureLocation(AbominationEntity entity) {
        return TEXTURE;
    }
}
