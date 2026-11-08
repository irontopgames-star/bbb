package com.example.pigabomination.config;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuracion del mod (archivo config/pigabomination-common.toml).
 *
 * Es una configuracion COMMON: se lee al arrancar el juego o el servidor (no por mundo). La vida y la velocidad
 * se aplican a las criaturas que se crean despues de arrancar, asi que esos cambios piden reiniciar.
 * El resto se lee cada vez que se usa, y Forge recarga el archivo si se edita con el juego abierto.
 *
 * Todos los valores tienen un minimo y un maximo: si alguien escribe un numero fuera de rango, Forge lo corrige
 * al valor por defecto y lo anota en el registro, en vez de romper la historia.
 */
public final class PigAbominationConfig {

    /** Un valor de configuracion con su valor por defecto de reserva, por si se lee antes de cargar el archivo. */
    public static final class Opt<T> {
        private final ForgeConfigSpec.ConfigValue<T> value;
        private final T fallback;

        private Opt(ForgeConfigSpec.ConfigValue<T> value, T fallback) {
            this.value = value;
            this.fallback = fallback;
        }

        public T get() {
            try {
                return value.get();
            } catch (IllegalStateException e) {
                // El archivo aun no se ha cargado (por ejemplo, al registrar atributos): vale el valor por defecto.
                return fallback;
            }
        }
    }

    /** Lista con un valor por noche (noches 1 a 4). */
    public static final class NightOpt {
        private final Opt<List<? extends Number>> opt;
        private final double[] fallback;

        private NightOpt(Opt<List<? extends Number>> opt, double[] fallback) {
            this.opt = opt;
            this.fallback = fallback;
        }

        /** Valor de la noche 1 a 4. Si la lista es mas corta de lo esperado, vale el valor por defecto. */
        public double get(int night) {
            int i = Math.max(0, Math.min(NIGHTS - 1, night - 1));
            List<? extends Number> list = opt.get();
            if (list != null && i < list.size() && list.get(i) != null) {
                return list.get(i).doubleValue();
            }
            return fallback[i];
        }

        public int getInt(int night) {
            return (int) Math.round(get(night));
        }
    }

    private static final int NIGHTS = 4;

    public static final ForgeConfigSpec SPEC;

    // ---- La abominacion (jefe) ----------------------------------------------------------------------------------
    public static final Opt<Double> BOSS_HEALTH;
    public static final Opt<Double> BOSS_SPEED;
    public static final Opt<Double> BITE_DAMAGE;
    public static final Opt<Double> CLAW_DAMAGE;
    public static final Opt<Double> CHARGE_DAMAGE;
    public static final Opt<Double> CHARGE_SPEED;
    public static final Opt<Double> ENRAGED_CHARGE_SPEED;
    public static final Opt<Double> CHARGE_COOLDOWN_SECONDS;
    public static final Opt<Double> ENRAGED_CHARGE_COOLDOWN_SECONDS;
    public static final Opt<Double> ENRAGE_AT_HEALTH;
    public static final Opt<Integer> XP_REWARD;

    // ---- El cerdo normal ----------------------------------------------------------------------------------------
    public static final Opt<Double> PIG_HEALTH;
    public static final Opt<Double> PIG_SPEED;

    // ---- Apariciones ---------------------------------------------------------------------------------------------
    public static final Opt<Boolean> ENABLE_DAY_PIG;
    public static final Opt<Double> DAY_PIG_CHANCE;
    public static final Opt<Double> DAY_PIG_COOLDOWN_SECONDS;
    public static final Opt<Boolean> ENABLE_NIGHT_APPEARANCES;
    public static final NightOpt NIGHT_CHANCE;
    public static final NightOpt MAX_PER_NIGHT;
    public static final NightOpt GAP_SECONDS;
    public static final Opt<Double> ENCOUNTER_SECONDS;
    public static final Opt<Double> FLASH_SECONDS;
    public static final Opt<Double> NIGHT_FOUR_HIT_DAMAGE;

    // ---- Golpes en puertas y ventanas ---------------------------------------------------------------------------
    public static final Opt<Boolean> ENABLE_KNOCKS;
    public static final NightOpt KNOCK_MIN_SECONDS;
    public static final NightOpt KNOCK_RANDOM_SECONDS;
    public static final Opt<Double> BREAK_CHANCE;
    public static final Opt<Integer> MAX_BREAKS_PER_NIGHT;

    // ---- Restos de animales -------------------------------------------------------------------------------------
    public static final Opt<Boolean> ENABLE_REMAINS;
    public static final NightOpt REMAINS_CHANCE;
    public static final Opt<Integer> MAX_REMAINS_NEAR;
    public static final Opt<Double> CORPSE_SECONDS;
    public static final Opt<Double> HEAD_SECONDS;

    // ---- Mundo ---------------------------------------------------------------------------------------------------
    public static final Opt<Boolean> ENABLE_FINAL_HUNT;
    public static final Opt<Boolean> HUNT_ANIMALS;
    public static final Opt<Boolean> ALLOW_BLOCK_BREAKING;

    // ---- Sonido --------------------------------------------------------------------------------------------------
    public static final Opt<Double> TAUNT_VOLUME;
    public static final Opt<Double> THREAT_VOLUME;
    public static final Opt<Double> SCREAM_VOLUME;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.comment("La abominacion: la pelea final del dia 5.",
                "Vida y velocidad se aplican al reiniciar el juego o el servidor.").push("boss");
        BOSS_HEALTH = dbl(b, "health", "Vida maxima (300 = 150 corazones).", 300.0D, 1.0D, 100000.0D);
        BOSS_SPEED = dbl(b, "movementSpeed", "Velocidad base al perseguirte. Enfurecida corre un 20% mas.",
                0.28D, 0.05D, 1.0D);
        BITE_DAMAGE = dbl(b, "biteDamage", "Dano del mordisco (2 = 1 corazon).", 8.0D, 0.0D, 1000.0D);
        CLAW_DAMAGE = dbl(b, "clawDamage", "Dano del aranazo.", 6.0D, 0.0D, 1000.0D);
        CHARGE_DAMAGE = dbl(b, "chargeDamage", "Dano de la embestida.", 12.0D, 0.0D, 1000.0D);
        CHARGE_SPEED = dbl(b, "chargeSpeed", "Velocidad de la embestida, en bloques por tick.",
                0.55D, 0.1D, 2.0D);
        ENRAGED_CHARGE_SPEED = dbl(b, "enragedChargeSpeed", "Velocidad de la embestida cuando esta enfurecida.",
                0.62D, 0.1D, 2.0D);
        CHARGE_COOLDOWN_SECONDS = dbl(b, "chargeCooldownSeconds",
                "Segundos minimos entre dos embestidas (se le suman de 0 a 5 al azar).", 9.0D, 1.0D, 600.0D);
        ENRAGED_CHARGE_COOLDOWN_SECONDS = dbl(b, "enragedChargeCooldownSeconds",
                "Lo mismo, cuando esta enfurecida (se le suman de 0 a 3 al azar).", 5.5D, 1.0D, 600.0D);
        ENRAGE_AT_HEALTH = dbl(b, "enrageAtHealth",
                "Fraccion de vida a la que se enfurece (0.5 = a la mitad; 0 = nunca).", 0.5D, 0.0D, 1.0D);
        XP_REWARD = integer(b, "xpReward", "Experiencia al derrotarla.", 500, 0, 100000);
        b.pop();

        b.comment("El cerdo normal, el que se acerca a mirarte de dia.",
                "Vida y velocidad se aplican al reiniciar el juego o el servidor.").push("pig");
        PIG_HEALTH = dbl(b, "health", "Vida maxima.", 300.0D, 1.0D, 100000.0D);
        PIG_SPEED = dbl(b, "movementSpeed", "Velocidad base.", 0.2D, 0.05D, 1.0D);
        b.pop();

        b.comment("Cuando y con que frecuencia aparecen el cerdo y la abominacion.",
                "Las listas tienen cuatro numeros: noche 1, 2, 3 y 4.").push("appearances");
        ENABLE_DAY_PIG = bool(b, "enableDayPig", "El cerdo que te mira de dia (dias 1 a 4).", true);
        DAY_PIG_CHANCE = dbl(b, "dayPigChance",
                "Probabilidad por segundo de que salga el cerdo de dia (0.002 = cada 8 minutos de media).",
                0.002D, 0.0D, 1.0D);
        DAY_PIG_COOLDOWN_SECONDS = dbl(b, "dayPigCooldownSeconds", "Segundos minimos entre dos cerdos de dia.",
                600.0D, 0.0D, 86400.0D);
        ENABLE_NIGHT_APPEARANCES = bool(b, "enableNightAppearances",
                "Las apariciones de la abominacion en las noches 1 a 4.", true);
        NIGHT_CHANCE = nights(b, "nightChance", "Probabilidad por segundo de una aparicion.",
                new double[] {0.008D, 0.012D, 0.016D, 0.022D}, 0.0D, 1.0D);
        MAX_PER_NIGHT = nights(b, "maxPerNight", "Apariciones maximas por noche.",
                new double[] {2.0D, 2.0D, 3.0D, 3.0D}, 0.0D, 100.0D);
        GAP_SECONDS = nights(b, "gapSeconds", "Segundos minimos entre dos apariciones de la misma noche.",
                new double[] {300.0D, 150.0D, 200.0D, 150.0D}, 0.0D, 86400.0D);
        ENCOUNTER_SECONDS = dbl(b, "encounterSeconds", "Segundos que dura una aparicion antes de retirarse.",
                45.0D, 1.0D, 3600.0D);
        FLASH_SECONDS = dbl(b, "flashSeconds",
                "Segundos que dura la aparicion repentina a tu lado si no la miras.", 3.0D, 1.0D, 3600.0D);
        NIGHT_FOUR_HIT_DAMAGE = dbl(b, "nightFourHitDamage",
                "Dano del golpe de las apariciones de la noche 4 (4 = 2 corazones; 0 = no hacen dano).",
                4.0D, 0.0D, 1000.0D);
        b.pop();

        b.comment("Golpes en puertas y ventanas con la casa en calma.").push("knocks");
        ENABLE_KNOCKS = bool(b, "enableKnocks", "Puertas y ventanas suenan como si alguien llamara.", true);
        KNOCK_MIN_SECONDS = nights(b, "minSeconds", "Segundos minimos entre dos golpes.",
                new double[] {30.0D, 22.0D, 16.0D, 10.0D}, 1.0D, 3600.0D);
        KNOCK_RANDOM_SECONDS = nights(b, "randomSeconds", "Segundos extra al azar entre dos golpes.",
                new double[] {16.0D, 14.0D, 10.0D, 8.0D}, 1.0D, 3600.0D);
        BREAK_CHANCE = dbl(b, "breakChance",
                "Noches 2 y 4: probabilidad de que un golpe acabe rompiendo la puerta o el cristal.",
                0.6D, 0.0D, 1.0D);
        MAX_BREAKS_PER_NIGHT = integer(b, "maxBreaksPerNight", "Roturas maximas por noche.", 3, 0, 100);
        b.pop();

        b.comment("Cadaveres y cabezas de animales junto a la casa.").push("remains");
        ENABLE_REMAINS = bool(b, "enableRemains", "Aparecen restos de animales.", true);
        REMAINS_CHANCE = nights(b, "chance", "Probabilidad por segundo de que aparezcan restos (0 = nunca).",
                new double[] {0.006D, 0.0D, 0.0D, 0.012D}, 0.0D, 1.0D);
        MAX_REMAINS_NEAR = integer(b, "maxNear", "Maximo de restos a menos de 24 bloques del jugador.", 3, 0, 100);
        CORPSE_SECONDS = dbl(b, "corpseSeconds", "Segundos que dura un cadaver de las noches 3 y 4.",
                120.0D, 1.0D, 86400.0D);
        HEAD_SECONDS = dbl(b, "headSeconds", "Segundos que dura una cabeza de la noche 4.",
                120.0D, 1.0D, 86400.0D);
        b.pop();

        b.comment("Reglas generales del mod.").push("world");
        ENABLE_FINAL_HUNT = bool(b, "enableFinalHunt",
                "La transformacion y la pelea final del dia 5 al anochecer.", true);
        HUNT_ANIMALS = bool(b, "huntAnimals",
                "La abominacion de la noche 3 caza vacas, ovejas, pollos y cerdos.", true);
        ALLOW_BLOCK_BREAKING = bool(b, "allowBlockBreaking",
                "Puede romper puertas, cristales y muros. Ademas hace falta la regla mobGriefing.", true);
        b.pop();

        b.comment("Volumen de las voces (1.0 = normal).").push("audio");
        TAUNT_VOLUME = dbl(b, "tauntVolume", "Voz \"tienes miedo\" de las noches 2 a 4.", 1.5D, 0.0D, 5.0D);
        THREAT_VOLUME = dbl(b, "threatVolume", "Voz \"voy a matarte\" de la transformacion.", 2.0D, 0.0D, 5.0D);
        SCREAM_VOLUME = dbl(b, "screamVolume", "Grito al golpear a la abominacion.", 1.6D, 0.0D, 5.0D);
        b.pop();

        SPEC = b.build();
    }

    private PigAbominationConfig() {}

    /** Segundos a ticks, como minimo 1. */
    public static int ticks(Opt<Double> seconds) {
        return (int) Math.max(1L, Math.round(seconds.get() * 20.0D));
    }

    private static Opt<Double> dbl(ForgeConfigSpec.Builder b, String path, String comment, double def, double min,
                                   double max) {
        return new Opt<>(b.comment(comment).defineInRange(path, def, min, max), def);
    }

    private static Opt<Integer> integer(ForgeConfigSpec.Builder b, String path, String comment, int def, int min,
                                        int max) {
        return new Opt<>(b.comment(comment).defineInRange(path, def, min, max), def);
    }

    private static Opt<Boolean> bool(ForgeConfigSpec.Builder b, String path, String comment, boolean def) {
        return new Opt<>(b.comment(comment).define(path, def), def);
    }

    private static NightOpt nights(ForgeConfigSpec.Builder b, String path, String comment, double[] def, double min,
                                   double max) {
        List<Double> defaults = new ArrayList<>();
        for (double d : def) {
            defaults.add(d);
        }
        ForgeConfigSpec.ConfigValue<List<? extends Number>> value = b
                .comment(comment + " Cuatro numeros: noches 1 a 4.")
                .defineList(path, defaults,
                        o -> o instanceof Number n && n.doubleValue() >= min && n.doubleValue() <= max);
        List<? extends Number> fallback = defaults;
        return new NightOpt(new Opt<>(value, fallback), def);
    }
}
