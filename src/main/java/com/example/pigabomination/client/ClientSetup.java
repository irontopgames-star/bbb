package com.example.pigabomination.client;

import com.example.pigabomination.PigAbominationMod;
import com.example.pigabomination.registry.ModEntities;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = PigAbominationMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {

    @SubscribeEvent
    public static void onRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.PIG_ABOMINATION.get(), PigAbominationRenderer::new);
        event.registerEntityRenderer(ModEntities.ABOMINATION.get(), AbominationRenderer::new);
        event.registerEntityRenderer(ModEntities.ANIMAL_CORPSE.get(), AnimalCorpseRenderer::new);
        event.registerEntityRenderer(ModEntities.ANIMAL_HEAD.get(), AnimalHeadRenderer::new);
    }

    private ClientSetup() {}
}
