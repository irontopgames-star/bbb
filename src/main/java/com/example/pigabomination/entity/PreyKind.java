package com.example.pigabomination.entity;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.MushroomCow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Sheep;

/** Los animales que la abominacion puede cazar: vacas, ovejas, pollos y cerdos normales. */
public enum PreyKind {
    COW, SHEEP, CHICKEN, PIG;

    /** Tipo de presa de un animal, o null si no es una presa valida (p. ej. el champinon, que hereda de la vaca). */
    public static PreyKind of(Entity entity) {
        if (entity instanceof MushroomCow) {
            return null;
        }
        if (entity instanceof Cow) {
            return COW;
        }
        if (entity instanceof Sheep) {
            return SHEEP;
        }
        if (entity instanceof Chicken) {
            return CHICKEN;
        }
        if (entity instanceof Pig) {
            return PIG;
        }
        return null;
    }

    /** Sonido de impacto del propio animal (el de dano de vanilla). */
    public SoundEvent hurtSound() {
        return switch (this) {
            case COW -> SoundEvents.COW_HURT;
            case SHEEP -> SoundEvents.SHEEP_HURT;
            case CHICKEN -> SoundEvents.CHICKEN_HURT;
            case PIG -> SoundEvents.PIG_HURT;
        };
    }
}
