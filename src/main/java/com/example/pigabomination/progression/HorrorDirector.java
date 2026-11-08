package com.example.pigabomination.progression;

import com.example.pigabomination.config.PigAbominationConfig;
import com.example.pigabomination.entity.AbominationEntity;
import com.example.pigabomination.entity.AbominationEntity.Behavior;
import com.example.pigabomination.entity.AnimalCorpseEntity;
import com.example.pigabomination.entity.AnimalHeadEntity;
import com.example.pigabomination.entity.AnimalRemainsEntity;
import com.example.pigabomination.entity.PigAbominationEntity;
import com.example.pigabomination.entity.PreyKind;
import com.example.pigabomination.registry.ModEntities;
import com.example.pigabomination.registry.ModSounds;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.GlassBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Decide que aparece, donde y cuando:
 * - De dia (dias 1 a 5): el cerdo normal se acerca a mirarte, suena al aparecer y al irse.
 * - De noche (noches 1 a 4): la abominacion aparece varias veces por noche (ventana, puerta o de repente a tu
 *   lado). Cada noche es mas intensa. Golpea puertas y ventanas; en las noches 2 y 4 las rompe. En la noche 4
 *   sus apariciones hacen dano. Restos de animales en las noches 1 y 4.
 * - Dia 5 al anochecer: un cerdo se transforma en la abominacion durante unos 5 segundos (transform), el jefe
 *   surge (finishTransformation) y empieza la pelea (startHunt).
 */
public final class HorrorDirector {
    // Probabilidades, tiempos y cantidades: ver PigAbominationConfig (config/pigabomination-common.toml).

    /** Ultimo dia con cerdo de dia (dias 1 a 4). El dia 5 no sale; al anochecer empieza la transformacion. */
    private static final long LAST_DAY_PIG = 4L;
    /**
     * La voz "tienes miedo" suena 2 s despues de la aparicion: el zumbido de la aparicion es fuerte los primeros
     * 2 s y despues se apaga, y asi se entiende la voz.
     */
    private static final int TAUNT_DELAY = 2;
    /** Un cerdo a menos de esta distancia (bloques) es el que se transforma; mas lejos no se veria. */
    private static final double TRANSFORM_PIG_RANGE = 48.0D;
    /** Segundos entre el golpe y la rotura. */
    private static final int BREAK_DELAY = 2;
    /** Radio (bloques) en el que se cuentan los restos de animales ya puestos. */
    private static final double REMAINS_RADIUS = 24.0D;

    private static int nightCooldown;
    private static int dayCooldown;
    /** Segundos hasta el siguiente golpe de calma. */
    private static int knockTimer = 20;
    /** Cuentas de la noche actual, identificada por su dia. */
    private static long countedNight = -1L;
    private static int appearancesTonight;
    private static int breaksTonight;
    /** Puerta o ventana que acaba de sonar y se rompera dentro de unos segundos. */
    /** "Tienes miedo": donde y en cuantos segundos suena la voz tras una aparicion de las noches 2 a 4. */
    private static BlockPos pendingTaunt;
    private static int pendingTauntIn;
    private static BlockPos pendingBreak;
    private static int pendingBreakIn;
    /** Jugador al que va dirigido el golpe: la rotura solo ocurre si sigue dentro de la casa. */
    private static ServerPlayer pendingBreakFor;
    /** Casillas maximas que se revisan para saber si el jugador esta dentro de la casa. */
    private static final int INDOOR_NODES = 1500;
    /** Si el hueco de aire alrededor del jugador se extiende mas de 14 bloques, esta fuera. */
    private static final double INDOOR_RADIUS_SQ = 14.0D * 14.0D;

    private HorrorDirector() {}

    /**
     * Borra todo lo que el director recuerda en memoria. Se llama al cerrar el servidor: en un mundo de un jugador,
     * estos campos estaticos sobrevivirian al siguiente mundo (y retendrian al jugador y al nivel anteriores).
     */
    public static void reset() {
        nightCooldown = 0;
        dayCooldown = 0;
        knockTimer = 20;
        countedNight = -1L;
        appearancesTonight = 0;
        breaksTonight = 0;
        pendingTaunt = null;
        pendingTauntIn = 0;
        pendingBreak = null;
        pendingBreakIn = 0;
        pendingBreakFor = null;
    }

    private static int lifetimeTicks() {
        return PigAbominationConfig.ticks(PigAbominationConfig.ENCOUNTER_SECONDS);
    }

    private static int flashLifetimeTicks() {
        return PigAbominationConfig.ticks(PigAbominationConfig.FLASH_SECONDS);
    }

    /** Llamado por HorrorNightManager cada 20 ticks (1 segundo). */
    public static void tick(ServerLevel level, long time, int started, boolean huntStarted) {
        if (nightCooldown > 0) {
            nightCooldown--;
        }
        if (dayCooldown > 0) {
            dayCooldown--;
        }
        tickPendingBreak(level);
        tickPendingTaunt(level);
        if (huntStarted) {
            return; // desde la caza final solo hay pelea, no apariciones
        }
        ServerPlayer target = pickTarget(level);
        if (target == null) {
            return;
        }
        if (HorrorNightManager.isNightTime(time)) {
            resetNightCounters(time);
            calmKnock(level, target, started);
            remainsEvent(level, target, started, time);
            if (nightCooldown > 0 || started < 1 || started > 4 || !PigAbominationConfig.ENABLE_NIGHT_APPEARANCES.get()) {
                return;
            }
            if (appearancesTonight >= PigAbominationConfig.MAX_PER_NIGHT.getInt(started)
                    || level.random.nextFloat() >= PigAbominationConfig.NIGHT_CHANCE.get(started)) {
                return;
            }
            if (!level.getEntitiesOfClass(AbominationEntity.class, target.getBoundingBox().inflate(80.0D)).isEmpty()) {
                return;
            }
            if (placeStalker(level, target, started, false, appearancesTonight)) {
                appearancesTonight++;
                nightCooldown = PigAbominationConfig.GAP_SECONDS.getInt(started);
            }
        } else {
            if (dayCooldown > 0 || HorrorNightManager.dayNumber(time) > LAST_DAY_PIG
                    || !PigAbominationConfig.ENABLE_DAY_PIG.get()) {
                return;
            }
            if (level.random.nextFloat() >= PigAbominationConfig.DAY_PIG_CHANCE.get()) {
                return;
            }
            if (!level.getEntitiesOfClass(PigAbominationEntity.class, target.getBoundingBox().inflate(80.0D)).isEmpty()) {
                return;
            }
            if (spawnPig(level, target, false)) {
                dayCooldown = (int) Math.round(PigAbominationConfig.DAY_PIG_COOLDOWN_SECONDS.get());
            }
        }
    }

    /** Al empezar cada noche, las cuentas de apariciones y roturas vuelven a cero. */
    private static void resetNightCounters(long time) {
        long key = HorrorNightManager.dayNumber(time);
        if (key != countedNight) {
            countedNight = key;
            appearancesTonight = 0;
            breaksTonight = 0;
        }
    }

    /**
     * Golpes de calma: de noche, sin ninguna criatura cerca, una puerta o una ventana cercana suena como si
     * alguien llamara. En las noches 2 y 4, a veces se rompe unos segundos despues, con una risa.
     */
    private static void calmKnock(ServerLevel level, ServerPlayer target, int started) {
        if (started < 1 || started > 4 || !PigAbominationConfig.ENABLE_KNOCKS.get()) {
            return;
        }
        if (--knockTimer > 0) {
            return;
        }
        knockTimer = PigAbominationConfig.KNOCK_MIN_SECONDS.getInt(started)
                + level.random.nextInt(Math.max(1, PigAbominationConfig.KNOCK_RANDOM_SECONDS.getInt(started)));
        if (!level.getEntitiesOfClass(AbominationEntity.class, target.getBoundingBox().inflate(80.0D)).isEmpty()
                || !level.getEntitiesOfClass(PigAbominationEntity.class, target.getBoundingBox().inflate(80.0D)).isEmpty()) {
            return;
        }
        // Los golpes, las roturas y la risa solo se oyen dentro de la casa.
        if (!isIndoors(level, target)) {
            return;
        }
        BlockPos opening = nearbyOpening(level, target);
        if (opening == null) {
            return;
        }
        boolean door = level.getBlockState(opening).getBlock() instanceof DoorBlock;
        level.playSound(null, opening.getX() + 0.5D, opening.getY() + 0.5D, opening.getZ() + 0.5D,
                door ? ModSounds.DOOR_KNOCK.get() : ModSounds.WINDOW_TAP.get(), SoundSource.AMBIENT, 1.0F, 1.0F);
        boolean breaks = started == 2 || started == 4;
        if (breaks && breaksTonight < PigAbominationConfig.MAX_BREAKS_PER_NIGHT.get()
                && level.random.nextFloat() < PigAbominationConfig.BREAK_CHANCE.get()) {
            pendingBreak = opening;
            pendingBreakFor = target;
            pendingBreakIn = BREAK_DELAY;
            breaksTonight++;
        }
    }

    /**
     * Rompe la puerta o la ventana que sono, con su risa. Lo que suena es lo que se rompe: una puerta si sono
     * la puerta, un cristal si sono el cristal.
     */
    /** Suena la voz "tienes miedo" donde aparecio la criatura, aunque ya se haya ido. */
    private static void tickPendingTaunt(ServerLevel level) {
        if (pendingTaunt == null || --pendingTauntIn > 0) {
            return;
        }
        BlockPos pos = pendingTaunt;
        pendingTaunt = null;
        level.playSound(null, pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D, ModSounds.TAUNT.get(),
                SoundSource.HOSTILE, PigAbominationConfig.TAUNT_VOLUME.get().floatValue(), 1.0F);
    }

    private static void tickPendingBreak(ServerLevel level) {
        if (pendingBreak == null || --pendingBreakIn > 0) {
            return;
        }
        BlockPos pos = pendingBreak;
        ServerPlayer who = pendingBreakFor;
        pendingBreak = null;
        pendingBreakFor = null;
        // Si el jugador ya no esta dentro de la casa (o cambio de dimension), nada se rompe.
        if (who == null || who.isRemoved() || who.level() != level || !isIndoors(level, who)) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        boolean breakable = state.getBlock() instanceof DoorBlock
                || state.getBlock() instanceof GlassBlock
                || state.getBlock() instanceof IronBarsBlock;
        if (!breakable || !PigAbominationConfig.ALLOW_BLOCK_BREAKING.get()
                || !level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            return;
        }
        level.destroyBlock(pos, false);
        level.playSound(null, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                ModSounds.LAUGH.get(), SoundSource.HOSTILE, 1.0F, 1.0F);
    }

    /**
     * Dentro de la casa: el aire alrededor del jugador esta cerrado (paredes, puertas cerradas, cristales) y no
     * se ve el cielo. Se expande por el aire desde la cabeza del jugador; si llega al cielo, se aleja demasiado
     * o ocupa demasiadas casillas, esta fuera.
     */
    private static boolean isIndoors(ServerLevel level, ServerPlayer player) {
        BlockPos start = player.blockPosition().above();
        if (level.canSeeSky(start)) {
            return false;
        }
        Set<BlockPos> seen = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        seen.add(start);
        queue.add(start);
        while (!queue.isEmpty()) {
            BlockPos cur = queue.poll();
            if (level.canSeeSky(cur) || seen.size() > INDOOR_NODES) {
                return false;
            }
            for (Direction dir : Direction.values()) {
                BlockPos next = cur.relative(dir);
                if (seen.contains(next) || !isAir(level, next)) {
                    continue;
                }
                double dx = next.getX() - start.getX();
                double dy = next.getY() - start.getY();
                double dz = next.getZ() - start.getZ();
                if (dx * dx + dy * dy + dz * dz > INDOOR_RADIUS_SQ) {
                    return false;
                }
                seen.add(next);
                queue.add(next);
            }
        }
        return true;
    }

    /** Aire por el que se puede pasar: sin colision. Una puerta, un cristal o una reja bloquean. */
    private static boolean isAir(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    /**
     * Restos de animales junto a la casa, en calma. Noche 1: un cadaver en el jardin y una cabeza en la puerta,
     * que duran hasta el anochecer. Noche 4: una cabeza o un cadaver, por poco tiempo.
     */
    private static void remainsEvent(ServerLevel level, ServerPlayer target, int started, long time) {
        if (started < 1 || started > 4 || !PigAbominationConfig.ENABLE_REMAINS.get()) {
            return;
        }
        double chance = PigAbominationConfig.REMAINS_CHANCE.get(started);
        if (chance <= 0.0D || level.random.nextFloat() >= chance) {
            return;
        }
        if (!level.getEntitiesOfClass(AbominationEntity.class, target.getBoundingBox().inflate(80.0D)).isEmpty()
                || !level.getEntitiesOfClass(PigAbominationEntity.class, target.getBoundingBox().inflate(80.0D)).isEmpty()) {
            return;
        }
        if (level.getEntitiesOfClass(AnimalRemainsEntity.class, target.getBoundingBox().inflate(REMAINS_RADIUS))
                .size() >= PigAbominationConfig.MAX_REMAINS_NEAR.get()) {
            return;
        }
        if (started == 1) {
            int life = dawnLife(time);
            placeCorpse(level, target, true, life);
            placeHead(level, target, life);
            return;
        }
        if (level.random.nextBoolean() && placeHead(level, target, AnimalRemainsEntity.headTicks())) {
            return;
        }
        placeCorpse(level, target, false, AnimalRemainsEntity.corpseTicks());
    }

    /** Ticks hasta el siguiente anochecer. Los restos de la noche 1 duran hasta entonces. */
    private static int dawnLife(long time) {
        long tod = HorrorNightManager.timeOfDay(time);
        long untilNextNight = HorrorNightManager.DAY_LENGTH + HorrorNightManager.NIGHT_START - tod;
        return (int) Math.max(1L, untilNextNight);
    }

    /** Cabeza de animal en el suelo, junto a una puerta cercana, mirando hacia el jugador. */
    private static boolean placeHead(ServerLevel level, ServerPlayer target, int life) {
        BlockPos door = nearbyDoor(level, target);
        if (door == null) {
            return false;
        }
        // Fuera de la casa: de los cuatro lados de la puerta, el suelo mas lejos del jugador.
        BlockPos ground = null;
        double farthest = -1.0D;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos cand = door.relative(dir);
            BlockPos g = findGround(level, cand.getX(), cand.getZ(), door.getY());
            if (g == null) {
                continue;
            }
            double gx = g.getX() + 0.5D - target.getX();
            double gz = g.getZ() + 0.5D - target.getZ();
            if (gx * gx + gz * gz > farthest) {
                farthest = gx * gx + gz * gz;
                ground = g;
            }
        }
        if (ground == null) {
            return false;
        }
        AnimalHeadEntity head = ModEntities.ANIMAL_HEAD.get().create(level);
        if (head == null) {
            return false;
        }
        double dx = target.getX() - (ground.getX() + 0.5D);
        double dz = target.getZ() - (ground.getZ() + 0.5D);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        head.moveTo(ground.getX() + 0.5D, ground.getY(), ground.getZ() + 0.5D, yaw, 0.0F);
        head.setup(randomKind(level), life);
        level.addFreshEntity(head);
        return true;
    }

    /** Cadaver de animal en el jardin, a 5 a 9 bloques del jugador. */
    private static boolean placeCorpse(ServerLevel level, ServerPlayer target, boolean dawnFlies, int life) {
        double ang = level.random.nextDouble() * Math.PI * 2.0D;
        double dist = 5.0D + level.random.nextDouble() * 4.0D;
        BlockPos ground = groundAt(level, target, ang, dist, false);
        if (ground == null) {
            return false;
        }
        AnimalCorpseEntity corpse = ModEntities.ANIMAL_CORPSE.get().create(level);
        if (corpse == null) {
            return false;
        }
        corpse.moveTo(ground.getX() + 0.5D, ground.getY(), ground.getZ() + 0.5D,
                level.random.nextFloat() * 360.0F, 0.0F);
        corpse.setup(randomKind(level), life, dawnFlies);
        level.addFreshEntity(corpse);
        return true;
    }

    /** Puerta a menos de 8 bloques del jugador, elegida al azar. */
    private static BlockPos nearbyDoor(ServerLevel level, ServerPlayer player) {
        BlockPos center = player.blockPosition();
        List<BlockPos> doors = new ArrayList<>();
        for (int dx = -8; dx <= 8; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -8; dz <= 8; dz++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    if (level.getBlockState(pos).getBlock() instanceof DoorBlock) {
                        doors.add(pos.immutable());
                    }
                }
            }
        }
        return doors.isEmpty() ? null : doors.get(level.random.nextInt(doors.size()));
    }

    private static PreyKind randomKind(ServerLevel level) {
        PreyKind[] kinds = PreyKind.values();
        return kinds[level.random.nextInt(kinds.length)];
    }

    /** Puerta o ventana cerca del jugador, elegida al azar: lo que puede sonar y romperse. */
    private static BlockPos nearbyOpening(ServerLevel level, ServerPlayer player) {
        BlockPos center = player.blockPosition();
        List<BlockPos> openings = new ArrayList<>();
        for (int dx = -10; dx <= 10; dx++) {
            for (int dy = -2; dy <= 3; dy++) {
                for (int dz = -10; dz <= 10; dz++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    BlockState s = level.getBlockState(pos);
                    if (s.getBlock() instanceof DoorBlock || s.getBlock() instanceof GlassBlock
                            || s.getBlock() instanceof IronBarsBlock) {
                        openings.add(pos.immutable());
                    }
                }
            }
        }
        if (openings.isEmpty()) {
            return null;
        }
        return openings.get(level.random.nextInt(openings.size()));
    }

    /** Cerdo de dia: aparece lejos, se acerca a mirarte y se va. Con forced=true, delante de ti. */
    public static boolean spawnPig(ServerLevel level, ServerPlayer player, boolean forced) {
        BlockPos pos = openSpot(level, player, forced ? 10.0D : 14.0D, forced ? 14.0D : 20.0D, false, forced);
        if (pos == null && forced) {
            pos = openSpot(level, player, 14.0D, 20.0D, false, false);
        }
        if (pos == null) {
            return false;
        }
        PigAbominationEntity pig = ModEntities.PIG_ABOMINATION.get().create(level);
        if (pig == null) {
            return false;
        }
        pig.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
        if (!level.noCollision(pig)) {
            return false;
        }
        pig.setEncounter(PigAbominationEntity.Behavior.STALKING, lifetimeTicks());
        pig.lookAt(EntityAnchorArgument.Anchor.EYES, player.getEyePosition());
        level.addFreshEntity(pig);
        level.playSound(null, pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, SoundEvents.PIG_AMBIENT,
                SoundSource.HOSTILE, 0.8F, 0.6F);
        puff(level, pos, 12);
        return true;
    }

    /**
     * Abominacion de noche. Con forced=true (comando de prueba) ignora la hora y la oscuridad.
     * @return true si encontro un lugar valido y aparecio.
     */
    public static boolean spawnStalker(ServerLevel level, ServerPlayer player, int night, boolean forced) {
        return placeStalker(level, player, night, forced, 0);
    }

    /** index = cuantas apariciones llevamos esta noche: decide el tipo de aparicion. */
    private static boolean placeStalker(ServerLevel level, ServerPlayer player, int night, boolean forced, int index) {
        int n = Math.max(1, Math.min(4, night));
        Choice choice = pickStalkSpot(level, player, n, forced, index);
        if (choice == null) {
            return false;
        }
        AbominationEntity mob = ModEntities.ABOMINATION.get().create(level);
        if (mob == null) {
            return false;
        }
        BlockPos pos = choice.pos();
        mob.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
        if (!level.noCollision(mob)) {
            return false;
        }
        int life = choice.kind() == Behavior.SIDE_FLASH ? flashLifetimeTicks() : lifetimeTicks();
        mob.setEncounter(choice.kind(), life);
        mob.setHurts(n == 4);
        mob.lookAt(EntityAnchorArgument.Anchor.EYES, player.getEyePosition());
        level.addFreshEntity(mob);
        level.playSound(null, pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, ModSounds.APPEAR.get(),
                SoundSource.HOSTILE, 1.0F, 1.0F);
        if (n >= 2) {
            // Solo en las apariciones de las noches 2 a 4: la voz llega cuando el zumbido se apaga.
            pendingTaunt = pos;
            pendingTauntIn = TAUNT_DELAY;
        }
        puff(level, pos, 12);
        return true;
    }

    /** Dia 5 al anochecer: transforma al cerdo cercano y empieza la pelea. @return true si se hizo. */
    public static boolean startHunt(ServerLevel level) {
        ServerPlayer target = pickTarget(level);
        return target != null && transform(level, target);
    }

    /**
     * Transformacion. Si hay un cerdo cerca (a menos de 48 bloques) se transforma ese; si no, aparece uno delante
     * de ti, que te mira un momento y empieza a cambiar. Son unos 5 segundos (ver PigAbominationEntity) y despues
     * surge la abominacion (finishTransformation). Si no hay sitio para el cerdo, el jefe surge directamente.
     */
    public static boolean transform(ServerLevel level, ServerPlayer target) {
        PigAbominationEntity pig = nearestPig(level, target);
        if (pig != null && pig.distanceTo(target) > TRANSFORM_PIG_RANGE) {
            pig.discard(); // un cerdo lejano no se veria: se sustituye por uno delante de ti
            pig = null;
        }
        if (pig == null) {
            pig = spawnTransformPig(level, target);
        }
        if (pig != null) {
            pig.beginTransformation(target);
            return true;
        }
        BlockPos spot = openSpot(level, target, 8.0D, 12.0D, false, true);
        if (spot == null) {
            spot = openSpot(level, target, 6.0D, 14.0D, false, false);
        }
        return spot != null && placeBoss(level, target, spot);
    }

    /** El cerdo que se va a transformar: aparece delante de ti, a unos 10-14 bloques. */
    private static PigAbominationEntity spawnTransformPig(ServerLevel level, ServerPlayer target) {
        BlockPos pos = openSpot(level, target, 10.0D, 14.0D, false, true);
        if (pos == null) {
            pos = openSpot(level, target, 8.0D, 16.0D, false, false);
        }
        if (pos == null) {
            return null;
        }
        PigAbominationEntity pig = ModEntities.PIG_ABOMINATION.get().create(level);
        if (pig == null) {
            return null;
        }
        pig.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
        if (!level.noCollision(pig)) {
            return null;
        }
        pig.lookAt(EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
        level.addFreshEntity(pig);
        level.playSound(null, pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, SoundEvents.PIG_AMBIENT,
                SoundSource.HOSTILE, 0.8F, 0.6F);
        puff(level, pos, 12);
        return pig;
    }

    /**
     * Fin de la transformacion: el cerdo desaparece y surge el jefe en su lugar (o en el hueco libre mas cercano).
     * @return true si el jefe ya esta en el mundo y el cerdo se ha retirado.
     */
    public static boolean finishTransformation(ServerLevel level, PigAbominationEntity pig, Player target) {
        BlockPos at = pig.blockPosition();
        // El jefe mide 2,7 de alto: si el cerdo esta bajo un techo bajo, prueba los huecos de al lado.
        int[][] around = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, -1}, {1, -1}, {-1, 1}, {2, 0},
                {-2, 0}, {0, 2}, {0, -2}};
        for (int[] d : around) {
            if (placeBoss(level, target, at.offset(d[0], 0, d[1]))) {
                pig.discard();
                return true;
            }
        }
        if (target instanceof ServerPlayer sp) {
            BlockPos spot = openSpot(level, sp, 6.0D, 14.0D, false, false);
            if (spot != null && placeBoss(level, target, spot)) {
                pig.discard();
                return true;
            }
        }
        return false;
    }

    private static boolean placeBoss(ServerLevel level, Player target, BlockPos pos) {
        AbominationEntity boss = ModEntities.ABOMINATION.get().create(level);
        if (boss == null) {
            return false;
        }
        boss.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
        if (!level.noCollision(boss)) {
            return false;
        }
        boss.lookAt(EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
        level.addFreshEntity(boss);
        boss.startFight(target);
        puff(level, pos, 40);
        return true;
    }

    private static ServerPlayer pickTarget(ServerLevel level) {
        List<ServerPlayer> players = new ArrayList<>();
        for (ServerPlayer p : level.players()) {
            if (!p.isSpectator()) {
                players.add(p);
            }
        }
        if (players.isEmpty()) {
            return null;
        }
        return players.get(level.random.nextInt(players.size()));
    }

    private static PigAbominationEntity nearestPig(ServerLevel level, ServerPlayer target) {
        List<PigAbominationEntity> pigs =
                level.getEntitiesOfClass(PigAbominationEntity.class, target.getBoundingBox().inflate(128.0D));
        PigAbominationEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (PigAbominationEntity pig : pigs) {
            if (pig.isTransforming()) {
                continue; // ya esta cambiando: no se reinicia
            }
            double d = pig.distanceToSqr(target);
            if (d < bestD) {
                bestD = d;
                best = pig;
            }
        }
        return best;
    }

    private record Choice(BlockPos pos, Behavior kind) {}

    /**
     * Donde aparece la abominacion, segun la noche y cuantas apariciones van esa noche.
     * Noche 1: a tu lado. Noche 2: primero ventana, despues a tu lado. Noche 3: ventana o te acecha desde lejos.
     * Noche 4: puerta, luego ventana, y a partir de la tercera a tu lado.
     * Con el comando de prueba aparece delante de ti.
     */
    private static Choice pickStalkSpot(ServerLevel level, ServerPlayer player, int night, boolean forced, int index) {
        Vec3 eye = player.getEyePosition();
        boolean sudden = night == 1 || (night == 2 && index == 1) || (night == 4 && index >= 2);
        if (night == 4 && index == 0) {
            BlockPos door = outsideSpot(level, player, eye, true);
            if (door != null) {
                return new Choice(door, Behavior.DOOR_STARE);
            }
        }
        if (night >= 2 && !sudden) {
            BlockPos window = outsideSpot(level, player, eye, false);
            if (window != null) {
                return new Choice(window, Behavior.WINDOW_STARE);
            }
        }
        if (night == 3) {
            BlockPos near = openSpot(level, player, 10.0D, 14.0D, false, forced);
            if (near != null) {
                return new Choice(near, Behavior.STALKING);
            }
        }
        if (forced) {
            BlockPos front = openSpot(level, player, 10.0D, 14.0D, false, true);
            if (front != null) {
                return new Choice(front, Behavior.WATCHING);
            }
        } else {
            BlockPos side = sideSpot(level, player);
            if (side != null) {
                return new Choice(side, Behavior.SIDE_FLASH);
            }
        }
        BlockPos far = openSpot(level, player, 14.0D, 20.0D, false, false);
        return far == null ? null : new Choice(far, Behavior.WATCHING);
    }

    /** Suelo valido a distancia del jugador. aheadOnly: dentro de unos 40 grados de lo que miras. */
    private static BlockPos openSpot(ServerLevel level, ServerPlayer player, double minD, double maxD,
                                     boolean requireDark, boolean aheadOnly) {
        double facing = facingAngle(player);
        for (int i = 0; i < 12; i++) {
            double ang = aheadOnly
                    ? facing + (level.random.nextDouble() * 2.0D - 1.0D) * Math.toRadians(40.0D)
                    : level.random.nextDouble() * Math.PI * 2.0D;
            double dist = minD + level.random.nextDouble() * (maxD - minD);
            BlockPos spot = groundAt(level, player, ang, dist, requireDark);
            if (spot != null) {
                return spot;
            }
        }
        return null;
    }

    /** Lado del jugador: entre 60 y 150 grados a izquierda o derecha de lo que mira, a 3,5 a 6 bloques. */
    private static BlockPos sideSpot(ServerLevel level, ServerPlayer player) {
        double facing = facingAngle(player);
        for (int i = 0; i < 12; i++) {
            double side = Math.toRadians(60.0D + level.random.nextDouble() * 90.0D);
            if (level.random.nextBoolean()) {
                side = -side;
            }
            double dist = 3.5D + level.random.nextDouble() * 2.5D;
            BlockPos spot = groundAt(level, player, facing + side, dist, false);
            if (spot != null) {
                return spot;
            }
        }
        return null;
    }

    /** Direccion en la que mira el jugador, en el mismo sistema que cos/sin de groundAt. */
    private static double facingAngle(ServerPlayer player) {
        double yaw = Math.toRadians(player.getYRot());
        return Math.atan2(Math.cos(yaw), -Math.sin(yaw));
    }

    private static BlockPos groundAt(ServerLevel level, ServerPlayer player, double ang, double dist,
                                     boolean requireDark) {
        int x = Mth.floor(player.getX() + Math.cos(ang) * dist);
        int z = Mth.floor(player.getZ() + Math.sin(ang) * dist);
        BlockPos ground = findGround(level, x, z, Mth.floor(player.getY()));
        if (ground == null) {
            return null;
        }
        if (requireDark && level.getMaxLocalRawBrightness(ground.above()) > 7) {
            return null;
        }
        return ground;
    }

    /**
     * Busca un suelo firme con espacio libre para el cuerpo (3 bloques de altura), cerca de centerY.
     * Devuelve la posicion donde estarian los pies.
     */
    private static BlockPos findGround(ServerLevel level, int x, int z, int centerY) {
        for (int y = centerY + 4; y >= centerY - 6; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            BlockPos below = pos.below();
            if (freeSpace(level, pos)
                    && freeSpace(level, pos.above())
                    && freeSpace(level, pos.above(2))
                    && level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
                return pos;
            }
        }
        return null;
    }

    /** Hueco libre: aire, o hierba, flores y similares (sin colision ni agua). */
    private static boolean freeSpace(ServerLevel level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        return s.isAir() || (s.getCollisionShape(level, pos).isEmpty() && s.getFluidState().isEmpty());
    }

    /**
     * Ventana (cristal o reja) o puerta abierta cerca del jugador, con la criatura del lado de fuera:
     * el punto de pie queda detras de la abertura, alejado del jugador y con linea de vision.
     */
    private static BlockPos outsideSpot(ServerLevel level, ServerPlayer player, Vec3 eye, boolean doors) {
        BlockPos center = player.blockPosition();
        List<BlockPos> openings = new ArrayList<>();
        for (int dx = -8; dx <= 8; dx++) {
            for (int dy = -2; dy <= 3; dy++) {
                for (int dz = -8; dz <= 8; dz++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    BlockState s = level.getBlockState(pos);
                    boolean ok = doors
                            ? (s.getBlock() instanceof DoorBlock && s.getValue(BlockStateProperties.OPEN))
                            : (s.getBlock() instanceof GlassBlock || s.getBlock() instanceof IronBarsBlock);
                    if (ok) {
                        openings.add(pos.immutable());
                    }
                }
            }
        }
        Collections.shuffle(openings, new Random(level.random.nextLong()));
        for (BlockPos w : openings) {
            double ax = w.getX() + 0.5D - player.getX();
            double az = w.getZ() + 0.5D - player.getZ();
            Direction best = null;
            double bestDot = 0.0D;
            for (Direction d : Direction.Plane.HORIZONTAL) {
                double dot = ax * d.getStepX() + az * d.getStepZ();
                if (dot > bestDot) {
                    bestDot = dot;
                    best = d;
                }
            }
            if (best == null) {
                continue;
            }
            for (int step = 2; step <= 3; step++) {
                BlockPos cand = w.relative(best, step);
                BlockPos ground = findGround(level, cand.getX(), cand.getZ(), w.getY());
                if (ground == null) {
                    continue;
                }
                Vec3 target = new Vec3(ground.getX() + 0.5D, ground.getY() + 1.6D, ground.getZ() + 0.5D);
                if (clearThroughGlass(level, eye, target, player)) {
                    return ground;
                }
            }
        }
        return null;
    }

    /** Linea de vision: libre, o atravesando solo cristal o reja. */
    private static boolean clearThroughGlass(ServerLevel level, Vec3 from, Vec3 to, ServerPlayer player) {
        BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player));
        if (hit.getType() == HitResult.Type.MISS) {
            return true;
        }
        BlockState hs = level.getBlockState(hit.getBlockPos());
        return hs.getBlock() instanceof GlassBlock || hs.getBlock() instanceof IronBarsBlock;
    }

    private static void puff(ServerLevel level, BlockPos pos, int count) {
        level.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D,
                count, 0.4D, 0.6D, 0.4D, 0.02D);
    }
}
