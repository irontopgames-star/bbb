package com.example.pigabomination.progression;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Progreso de la historia en el mundo: ultima noche iniciada, si ya empezo la caza final y si se vencio al jefe.
 * Vive en el DataStorage del Overworld: es unico por mundo y se guarda junto con el mundo.
 */
public class HorrorWorldData extends SavedData {
    private static final String NAME = "pigabomination_progress";

    private int lastNight;
    private boolean huntStarted;
    private boolean bossDefeated;

    public static HorrorWorldData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(HorrorWorldData::load, HorrorWorldData::new, NAME);
    }

    public static HorrorWorldData load(CompoundTag tag) {
        HorrorWorldData d = new HorrorWorldData();
        d.lastNight = tag.getInt("LastNight");
        d.huntStarted = tag.getBoolean("HuntStarted");
        d.bossDefeated = tag.getBoolean("BossDefeated");
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("LastNight", lastNight);
        tag.putBoolean("HuntStarted", huntStarted);
        tag.putBoolean("BossDefeated", bossDefeated);
        return tag;
    }

    public int getLastNight() {
        return lastNight;
    }

    public boolean isHuntStarted() {
        return huntStarted;
    }

    /** true si los jugadores ya vencieron a la abominacion: la historia termino. */
    public boolean isBossDefeated() {
        return bossDefeated;
    }

    public void setLastNight(int night) {
        if (night != lastNight) {
            lastNight = night;
            setDirty();
        }
    }

    public void markHuntStarted() {
        if (!huntStarted) {
            huntStarted = true;
            setDirty();
        }
    }

    public void markBossDefeated() {
        if (!bossDefeated) {
            bossDefeated = true;
            setDirty();
        }
    }

    public void reset() {
        lastNight = 0;
        huntStarted = false;
        bossDefeated = false;
        setDirty();
    }
}
