package com.example.pigabomination.client;

import com.example.pigabomination.entity.AnimalCorpseEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

/** El cadaver de un animal, tumbado de costado. */
public class AnimalCorpseRenderer extends AnimalRemainsRenderer<AnimalCorpseEntity> {
    public AnimalCorpseRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, true);
    }
}
