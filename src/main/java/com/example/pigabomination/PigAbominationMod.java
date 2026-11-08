package com.example.pigabomination;

import com.example.pigabomination.config.PigAbominationConfig;
import com.example.pigabomination.registry.ModCreativeTabs;
import com.example.pigabomination.registry.ModEntities;
import com.example.pigabomination.registry.ModItems;
import com.example.pigabomination.registry.ModSounds;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(PigAbominationMod.MODID)
public class PigAbominationMod {
    public static final String MODID = "pigabomination";

    public PigAbominationMod() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();

        ModEntities.ENTITIES.register(bus);
        ModItems.ITEMS.register(bus);
        ModCreativeTabs.TABS.register(bus);
        ModSounds.SOUNDS.register(bus);

        // Fase 8: config/pigabomination-common.toml
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, PigAbominationConfig.SPEC);
    }
}
