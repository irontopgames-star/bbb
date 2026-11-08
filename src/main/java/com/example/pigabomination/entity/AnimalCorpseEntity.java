package com.example.pigabomination.entity;

import com.example.pigabomination.progression.HorrorNightManager;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/** Cadaver de un animal: tumbado en el suelo. Las moscas las dibuja el renderizador (sprite de fly.png). */
public class AnimalCorpseEntity extends AnimalRemainsEntity {

    public AnimalCorpseEntity(EntityType<? extends AnimalCorpseEntity> type, Level level) {
        super(type, level);
    }

    /** Los restos de la noche 1 solo tienen moscas cuando ya es de dia. El resto, siempre. */
    @Override
    public boolean hasFlies() {
        return !(isDawnFlies() && HorrorNightManager.isNightTime(this.level().getDayTime()));
    }
}
