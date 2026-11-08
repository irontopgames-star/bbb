package com.example.pigabomination.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/** Cabeza de un animal dejada junto a la puerta como aviso (noche 4). */
public class AnimalHeadEntity extends AnimalRemainsEntity {
    public AnimalHeadEntity(EntityType<? extends AnimalHeadEntity> type, Level level) {
        super(type, level);
    }
}
