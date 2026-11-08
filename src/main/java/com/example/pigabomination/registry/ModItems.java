package com.example.pigabomination.registry;

import com.example.pigabomination.PigAbominationMod;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, PigAbominationMod.MODID);

    public static final RegistryObject<Item> PIG_ABOMINATION_SPAWN_EGG = ITEMS.register("pig_abomination_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.PIG_ABOMINATION, 0xC98A8A, 0x4A1010, new Item.Properties()));

    private ModItems() {}
}
