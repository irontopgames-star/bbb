package com.example.pigabomination.registry;

import com.example.pigabomination.PigAbominationMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Los nombres coinciden con las claves de assets/pigabomination/sounds.json. */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, PigAbominationMod.MODID);

    private static RegistryObject<SoundEvent> reg(String name) {
        return SOUNDS.register(name,
                () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(PigAbominationMod.MODID, name)));
    }

    /** Dos golpes en una puerta. */
    public static final RegistryObject<SoundEvent> DOOR_KNOCK = reg("entity.pigabomination.door_knock");
    /** Tres toques de nudillos en cristal. */
    public static final RegistryObject<SoundEvent> WINDOW_TAP = reg("entity.pigabomination.window_tap");
    /** Gruñido de cerdo distorsionado. */
    public static final RegistryObject<SoundEvent> GRUNT = reg("entity.pigabomination.grunt");
    /** Aparicion repentina: zumbido grave y golpe metalico. */
    public static final RegistryObject<SoundEvent> APPEAR = reg("entity.pigabomination.appear");
    /** Gruñido corto durante la pelea. */
    public static final RegistryObject<SoundEvent> GROWL = reg("entity.pigabomination.growl");
    /** Rugido de la transformacion. */
    public static final RegistryObject<SoundEvent> ROAR = reg("entity.pigabomination.roar");
    /** Risa que acompana a la rotura de una puerta o una ventana (noches 2 y 4). */
    public static final RegistryObject<SoundEvent> LAUGH = reg("entity.pigabomination.laugh");

    /** Grito al recibir un golpe (grito.mp3 del autor). */
    public static final RegistryObject<SoundEvent> SCREAM = reg("entity.pigabomination.scream");
    /** "Voy a matarte": la voz del cerdo mientras se transforma el dia 5 (voy_a_matarte.mp3 del autor). */
    public static final RegistryObject<SoundEvent> THREAT = reg("entity.pigabomination.threat");
    /** "Tienes miedo": la voz de las apariciones de las noches 2 a 4 (tienes_miedo.mp3 del autor). */
    public static final RegistryObject<SoundEvent> TAUNT = reg("entity.pigabomination.taunt");

    private ModSounds() {}
}
