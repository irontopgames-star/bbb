package com.example.pigabomination.registry;

import com.example.pigabomination.PigAbominationMod;
import com.example.pigabomination.entity.AbominationEntity;
import com.example.pigabomination.entity.PigAbominationEntity;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Eventos del bus del mod: atributos de las dos criaturas. */
@Mod.EventBusSubscriber(modid = PigAbominationMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModEvents {

    @SubscribeEvent
    public static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.PIG_ABOMINATION.get(), PigAbominationEntity.createAttributes().build());
        event.put(ModEntities.ABOMINATION.get(), AbominationEntity.createAttributes().build());
    }

    private ModEvents() {}
}
