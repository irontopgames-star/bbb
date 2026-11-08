package com.example.pigabomination.registry;

import com.example.pigabomination.PigAbominationMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, PigAbominationMod.MODID);

    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.pigabomination"))
                    .icon(() -> new ItemStack(ModItems.PIG_ABOMINATION_SPAWN_EGG.get()))
                    .displayItems((params, out) -> out.accept(ModItems.PIG_ABOMINATION_SPAWN_EGG.get()))
                    .build());

    private ModCreativeTabs() {}
}
