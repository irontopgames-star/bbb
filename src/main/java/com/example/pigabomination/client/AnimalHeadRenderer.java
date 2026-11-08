package com.example.pigabomination.client;

import com.example.pigabomination.entity.AnimalHeadEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

/** La cabeza de un animal, apoyada en el suelo. */
public class AnimalHeadRenderer extends AnimalRemainsRenderer<AnimalHeadEntity> {
    public AnimalHeadRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, false);
    }
}
