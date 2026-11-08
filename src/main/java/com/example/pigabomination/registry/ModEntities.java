package com.example.pigabomination.registry;

import com.example.pigabomination.PigAbominationMod;
import com.example.pigabomination.entity.AbominationEntity;
import com.example.pigabomination.entity.AnimalCorpseEntity;
import com.example.pigabomination.entity.AnimalHeadEntity;
import com.example.pigabomination.entity.PigAbominationEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, PigAbominationMod.MODID);

    /** El cerdo de dia: te mira de vez en cuando. */
    public static final RegistryObject<EntityType<PigAbominationEntity>> PIG_ABOMINATION =
            ENTITIES.register("pig_abomination",
                    () -> EntityType.Builder.of(PigAbominationEntity::new, MobCategory.MONSTER)
                            .sized(1.0F, 2.4F)
                            .clientTrackingRange(10)
                            .build("pig_abomination"));

    /** La abominacion: acecha de noche y pelea el dia 5. */
    public static final RegistryObject<EntityType<AbominationEntity>> ABOMINATION =
            ENTITIES.register("abomination",
                    () -> EntityType.Builder.of(AbominationEntity::new, MobCategory.MONSTER)
                            .sized(1.6F, 2.7F)
                            .clientTrackingRange(10)
                            .build("abomination"));

    /** Cadaver de un animal cazado por la abominacion (noche 3), o colocado cerca de la casa (noche 4). */
    public static final RegistryObject<EntityType<AnimalCorpseEntity>> ANIMAL_CORPSE =
            ENTITIES.register("animal_corpse",
                    () -> EntityType.Builder.of(AnimalCorpseEntity::new, MobCategory.MISC)
                            .sized(0.9F, 0.5F)
                            .clientTrackingRange(10)
                            .build("animal_corpse"));

    /** Cabeza de animal junto a la puerta (noche 4). */
    public static final RegistryObject<EntityType<AnimalHeadEntity>> ANIMAL_HEAD =
            ENTITIES.register("animal_head",
                    () -> EntityType.Builder.of(AnimalHeadEntity::new, MobCategory.MISC)
                            .sized(0.5F, 0.5F)
                            .clientTrackingRange(10)
                            .build("animal_head"));

    private ModEntities() {}
}
