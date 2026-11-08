package com.example.pigabomination.progression;

import com.example.pigabomination.config.PigAbominationConfig;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

/**
 * Reloj de la historia. Todo se deduce del tiempo del mundo, no de la interfaz ni de un temporizador:
 * la noche N empieza al anochecer del dia N. Las noches 1 a 4 son de acecho; la noche 5 inicia la caza final.
 */
public final class HorrorNightManager {
    public static final long DAY_LENGTH = 24000L;
    /** Hora del mundo en la que empieza la noche (anochecer). */
    public static final long NIGHT_START = 13000L;
    /** Hora del mundo en la que termina la noche (amanecer). */
    public static final long NIGHT_END = 22500L;
    /** Noche en la que empieza la caza final (dia 5 al anochecer). */
    public static final int HUNT_NIGHT = 5;
    /** Cuantas noches recientes registrar si el tiempo salta varios dias de golpe. */
    private static final int MAX_CATCH_UP = 4;

    private static final Logger LOGGER = LogUtils.getLogger();

    public enum Phase { STALKING, HUNT, DEFEATED }

    private HorrorNightManager() {}

    public static long dayNumber(long time) {
        return time / DAY_LENGTH + 1L;
    }

    public static long timeOfDay(long time) {
        return time % DAY_LENGTH;
    }

    /** Cuantas noches han empezado ya en ese momento del mundo. */
    public static int startedNights(long time) {
        boolean night = timeOfDay(time) >= NIGHT_START;
        return (int) (dayNumber(time) - 1 + (night ? 1 : 0));
    }

    /** true si ahora es de noche (entre el anochecer y el amanecer). */
    public static boolean isNightTime(long time) {
        long tod = timeOfDay(time);
        return tod >= NIGHT_START && tod <= NIGHT_END;
    }

    public static Phase phase(HorrorWorldData data) {
        if (data.isBossDefeated()) {
            return Phase.DEFEATED;
        }
        return data.isHuntStarted() ? Phase.HUNT : Phase.STALKING;
    }

    /** Llamado por HorrorEvents cada 20 ticks en el servidor. */
    public static void check(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        HorrorWorldData data = HorrorWorldData.get(server);
        long time = overworld.getDayTime();
        int started = startedNights(time);

        if (started > data.getLastNight()) {
            for (int n = Math.max(data.getLastNight() + 1, started - MAX_CATCH_UP); n <= started; n++) {
                onNightStart(n);
            }
            data.setLastNight(started);
        }
        if (started >= HUNT_NIGHT && !data.isHuntStarted() && PigAbominationConfig.ENABLE_FINAL_HUNT.get()
                && HorrorDirector.startHunt(overworld)) {
            data.markHuntStarted();
            onHuntStart();
        }
        HorrorDirector.tick(overworld, time, started, data.isHuntStarted());
    }

    private static void onNightStart(int night) {
        LOGGER.info("The Pig Abomination: empieza la noche {}", night);
    }

    /** La transformacion ya ocurrio en HorrorDirector.startHunt; aqui solo queda el registro. */
    private static void onHuntStart() {
        LOGGER.info("The Pig Abomination: empieza la caza final");
    }
}
