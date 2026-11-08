package com.example.pigabomination.entity;

import com.example.pigabomination.config.PigAbominationConfig;
import com.example.pigabomination.progression.HorrorWorldData;
import com.example.pigabomination.registry.ModEntities;
import com.example.pigabomination.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * La abominacion ya transformada.
 *
 * Noches 1 a 4: acecha sin atacar (ventana, puerta, a tu lado o desde lejos).
 * Dia 5: surge de la transformacion (EMERGE) y empieza la pelea con la barra de vida. En la pelea persigue
 * encorvada y usa tres ataques con aviso (para poder esquivarlos): mordisco, arañazo y embestida en cuatro patas.
 * A mitad de vida se enfurece. Al morir cae despacio y la historia termina.
 *
 * La accion actual (AbominationAction) se sincroniza con el cliente, que anima el cuerpo con ella.
 */
public class AbominationEntity extends Monster {

    /** Estados de acecho (no atacan) y FIGHT (pelea del dia 5). */
    public enum Behavior {
        IDLE, SIDE_FLASH, WINDOW_STARE, DOOR_STARE, WATCHING, STALKING, RETREAT, DISAPPEAR, FIGHT
    }

    // ---- Acecho -------------------------------------------------------------------------------------------------

    /** Si el jugador se acerca a menos de esta distancia, la criatura se retira. */
    private static final double CLOSE_RANGE = 6.0D;
    private static final int RETREAT_TICKS = 80;
    /** Radio en el que los jugadores ven la barra de vida durante la pelea. */
    private static final double BOSS_RANGE = 64.0D;
    /** Caza (noche 3): radio para encontrar presas y distancia para golpear. */
    private static final double HUNT_RANGE = 12.0D;
    private static final double HUNT_REACH = 2.2D;
    /** Pausa tras una caza, y pausa entre busquedas si no hay presa. */
    private static final int HUNT_COOLDOWN = 200;
    private static final int HUNT_SEARCH_TICKS = 40;
    /** Si no alcanza a la presa en este tiempo, la deja. */
    private static final int HUNT_GIVE_UP_TICKS = 300;
    /** No caza mas si ya hay este numero de cadaveres en la zona. */
    private static final int MAX_CORPSES_NEAR = 4;
    private static final double CORPSE_RADIUS = 32.0D;
    /** Noche 4: si estas a menos de esta distancia, la aparicion te golpea una vez y desaparece. */
    private static final double HURT_RANGE = 4.0D;
    /** Mientras la aparicion esta a menos de esta distancia, la vision del jugador se oscurece. */
    private static final double DARK_RANGE = 24.0D;

    // ---- Pelea (vida, danos, velocidades y tiempos: ver PigAbominationConfig) -----------------------------------
    /** Alcance del golpe del mordisco y del arañazo (distancia horizontal, en bloques) y su cono (coseno). */
    private static final double BITE_REACH = 3.8D;
    private static final double BITE_CONE = 0.55D;
    private static final double CLAW_REACH = 4.4D;
    private static final double CLAW_CONE = 0.15D;
    /** A esta distancia empieza un ataque cuerpo a cuerpo. */
    private static final double BITE_START = 3.4D;
    private static final double CLAW_START = 4.2D;
    /** La embestida solo se usa entre estas distancias. */
    private static final double CHARGE_MIN = 7.0D;
    private static final double CHARGE_MAX = 22.0D;
    /** Ticks antes del golpe en los que la criatura deja de girarse hacia ti: ahi se puede esquivar. */
    private static final int DODGE_WINDOW = 5;
    /** Ticks que dura la muerte antes de desvanecerse. */
    private static final int DEATH_TICKS = 70;
    /** Grito al golpearla: ticks minimos entre dos gritos (el volumen esta en la configuracion). */
    private static final int SCREAM_GAP = 15;

    private static final EntityDataAccessor<Integer> DATA_ACTION =
            SynchedEntityData.defineId(AbominationEntity.class, EntityDataSerializers.INT);

    private final ServerBossEvent bossEvent = new ServerBossEvent(
            Component.literal("THE PIG ABOMINATION"), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);

    private Behavior behavior = Behavior.IDLE;
    private int lifetime;
    private int retreatTicks;
    /** Cuenta atras hasta el siguiente golpe o gruñido de acecho. */
    private int soundTimer = 60;
    /** Cuenta atras hasta el siguiente gruñido durante la pelea. */
    private int growlTimer = 200;
    /** Presa actual (null si no caza) y sus contadores. */
    private Animal prey;
    private int huntTicks;
    private int huntCooldown = 100;
    /** true en la noche 4: la aparicion te golpea una vez si estas cerca. */
    private boolean hurts;

    /** Ticks desde que empezo la accion actual (en el servidor y en el cliente). */
    private int actionTicks;
    private int attackCooldown;
    private int chargeCooldown;
    private boolean enraged;
    /** Tick (tickCount) del ultimo grito al recibir un golpe. */
    private int lastScream = -100;
    /** Direccion bloqueada de la embestida en curso. */
    private Vec3 chargeDir = Vec3.ZERO;

    public AbominationEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = PigAbominationConfig.XP_REWARD.get();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, PigAbominationConfig.BOSS_HEALTH.get())
                .add(Attributes.MOVEMENT_SPEED, PigAbominationConfig.BOSS_SPEED.get())
                .add(Attributes.ATTACK_DAMAGE, PigAbominationConfig.BITE_DAMAGE.get())
                .add(Attributes.FOLLOW_RANGE, 40.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_ACTION, 0);
    }

    // ---- Accion sincronizada ------------------------------------------------------------------------------------

    public AbominationAction getAction() {
        return AbominationAction.byId(this.entityData.get(DATA_ACTION));
    }

    /** Ticks de la accion actual con decimales, para animar suavemente en el cliente. */
    public float getActionTicks(float partialTick) {
        return this.actionTicks + partialTick;
    }

    private void setAction(AbominationAction action) {
        this.actionTicks = 0;
        this.entityData.set(DATA_ACTION, action.ordinal());
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_ACTION.equals(key)) {
            this.actionTicks = 0;
        }
    }

    /** Activa un encuentro de acecho: el estado y cuantos ticks dura antes de retirarse. */
    public void setEncounter(Behavior newBehavior, int lifetimeTicks) {
        this.behavior = newBehavior;
        this.lifetime = lifetimeTicks;
        this.retreatTicks = 0;
    }

    public Behavior getBehavior() {
        return behavior;
    }

    /** La abominacion no la borra la dificultad pacifica ni la distancia: la retira su propio encuentro o la pelea. */
    @Override
    public boolean shouldDespawnInPeaceful() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToPlayer) {
        return false;
    }

    /** Noche 4: las apariciones golpean al jugador que se acerca. */
    public void setHurts(boolean value) {
        this.hurts = value;
    }

    /**
     * Dia 5: empieza la pelea contra el jugador indicado. Primero surge (EMERGE, unos 4 segundos): no se la puede
     * herir y la barra de vida aun no se ve; al terminar ruge y empieza a atacar.
     */
    public void startFight(Player target) {
        this.behavior = Behavior.FIGHT;
        this.setPersistenceRequired();
        this.setTarget(target);
        this.bossEvent.setVisible(false);
        this.attackCooldown = 40;
        this.chargeCooldown = 120;
        setAction(AbominationAction.EMERGE);
    }

    @Override
    protected void registerGoals() {
        // Prioridad 0: el acecho manda sobre el movimiento, pero solo en los estados de acecho.
        this.goalSelector.addGoal(0, new StalkGoal());
        this.goalSelector.addGoal(1, new FloatGoal(this));
        // En la pelea, el movimiento y los ataques los lleva fightTick (cada tick).
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<Player>(this, Player.class, true) {
            @Override
            public boolean canUse() {
                return behavior == Behavior.FIGHT && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return behavior == Behavior.FIGHT && super.canContinueToUse();
            }
        });
    }

    private boolean isStalking() {
        return behavior != Behavior.IDLE && behavior != Behavior.FIGHT;
    }

    /** Toda la logica de acecho corre aqui, en el servidor. */
    private final class StalkGoal extends Goal {
        StalkGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return isStalking();
        }

        @Override
        public boolean canContinueToUse() {
            return isStalking();
        }

        @Override
        public void start() {
            getNavigation().stop();
        }

        @Override
        public void tick() {
            stalkTick();
        }
    }

    private void stalkTick() {
        Player p = this.level().getNearestPlayer(this, 40.0D);
        if (p == null) {
            vanish();
            return;
        }
        if (behavior != Behavior.RETREAT && behavior != Behavior.DISAPPEAR) {
            lifetime--;
            if (lifetime <= 0) {
                if (behavior == Behavior.SIDE_FLASH) {
                    vanish();
                    return;
                }
                startRetreat();
            }
        }
        double d = this.distanceTo(p);
        // Mientras la aparicion esta cerca, la vision del jugador se oscurece (noches 1 a 4).
        if (this.tickCount % 20 == 0 && d < DARK_RANGE
                && behavior != Behavior.RETREAT && behavior != Behavior.DISAPPEAR) {
            p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0, false, false));
        }
        if (hurts && d < HURT_RANGE && behavior != Behavior.RETREAT && behavior != Behavior.DISAPPEAR) {
            // Aparicion de la noche 4: un golpe de dos corazones y desaparece.
            p.hurt(this.damageSources().mobAttack(this), PigAbominationConfig.NIGHT_FOUR_HIT_DAMAGE.get().floatValue());
            playCreatureSound(ModSounds.GROWL.get(), 1.2F);
            vanish();
            return;
        }
        switch (behavior) {
            case SIDE_FLASH -> {
                if (hurts) {
                    // Noche 4: se lanza hacia ti y te golpea al llegar (ver arriba).
                    this.getNavigation().moveTo(p, 1.0D);
                } else if (lookingAt(p)) {
                    // Aparece a tu lado; se va en cuanto la miras.
                    vanish();
                } else {
                    stareAt(p);
                }
            }
            case WINDOW_STARE, DOOR_STARE, WATCHING -> {
                // En la noche 4 no se retira: te mira, y si te acercas, te golpea.
                if (d < CLOSE_RANGE && !hurts) {
                    startRetreat();
                } else {
                    stareAt(p);
                }
            }
            case STALKING -> {
                if (d < CLOSE_RANGE) {
                    startRetreat();
                } else if (hunt()) {
                    // Cazando: la presa tiene prioridad sobre el acecho.
                } else if (d > 14.0D) {
                    this.getNavigation().moveTo(p, 0.5D);
                } else {
                    stareAt(p);
                }
            }
            case RETREAT -> {
                retreatTicks--;
                if (retreatTicks <= 0 || d > 24.0D) {
                    behavior = Behavior.DISAPPEAR;
                } else {
                    moveAway(p, 0.7D);
                }
            }
            case DISAPPEAR -> vanish();
            default -> {
                // IDLE y FIGHT no pasan por aqui.
            }
        }
    }

    /** Gruñidos mientras acecha o te mira. */
    private void ambientSounds() {
        if (--soundTimer > 0) {
            return;
        }
        soundTimer = 160 + this.random.nextInt(200);
        switch (behavior) {
            case STALKING, WATCHING -> playCreatureSound(ModSounds.GRUNT.get(), 0.6F);
            default -> {
                // Silencio: las puertas y ventanas suenan en calma, desde HorrorDirector.
            }
        }
    }

    /** Durante la pelea, un gruñido de vez en cuando. */
    private void fightSounds() {
        if (--growlTimer > 0) {
            return;
        }
        growlTimer = 300 + this.random.nextInt(200);
        playCreatureSound(ModSounds.GROWL.get(), 1.2F);
    }

    private void playCreatureSound(SoundEvent sound, float volume) {
        playCreatureSound(sound, volume, 1.0F);
    }

    private void playCreatureSound(SoundEvent sound, float volume, float pitch) {
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(), sound, SoundSource.HOSTILE, volume, pitch);
    }

    /** true si el jugador mira casi de frente a la criatura. */
    private boolean lookingAt(Player p) {
        Vec3 toMe = this.position().add(0.0D, 1.2D, 0.0D).subtract(p.getEyePosition()).normalize();
        return p.getViewVector(1.0F).dot(toMe) > 0.85D;
    }

    private void stareAt(Player p) {
        this.getNavigation().stop();
        this.getLookControl().setLookAt(p, 30.0F, 30.0F);
    }

    private void startRetreat() {
        behavior = Behavior.RETREAT;
        retreatTicks = RETREAT_TICKS;
        prey = null;
    }

    private void moveAway(Player p, double speed) {
        Vec3 away = this.position().subtract(p.position());
        if (away.lengthSqr() < 1.0E-4D) {
            away = new Vec3(1.0D, 0.0D, 0.0D);
        }
        Vec3 target = this.position().add(away.normalize().scale(10.0D));
        this.getNavigation().moveTo(target.x, target.y, target.z, speed);
    }

    /**
     * Caza de animales (solo en acecho, noche 3): busca la presa mas cercana, se acerca, la golpea y la mata.
     * El golpe es el arañazo del jefe (accion CLAW): la presa muere en el instante del golpe.
     * @return true mientras persigue o golpea a una presa.
     */
    private boolean hunt() {
        if (prey != null && (!prey.isAlive() || prey.isRemoved())) {
            prey = null;
        }
        if (prey == null) {
            if (huntCooldown > 0) {
                huntCooldown--;
                return false;
            }
            prey = findPrey();
            if (prey == null) {
                huntCooldown = HUNT_SEARCH_TICKS;
                return false;
            }
            huntTicks = 0;
        }
        huntTicks++;
        if (huntTicks > HUNT_GIVE_UP_TICKS) {
            prey = null;
            huntCooldown = HUNT_COOLDOWN;
            return false;
        }
        if (prey.distanceTo(this) > HUNT_REACH) {
            if (huntTicks % 10 == 1) {
                this.getNavigation().moveTo(prey, 0.6D);
            }
            return true;
        }
        this.getNavigation().stop();
        this.getLookControl().setLookAt(prey, 30.0F, 30.0F);
        if (getAction() != AbominationAction.CLAW) {
            setAction(AbominationAction.CLAW);
        } else if (actionTicks >= AbominationAction.CLAW_STRIKE) {
            killPrey();
            prey = null;
            huntCooldown = HUNT_COOLDOWN;
        }
        return true;
    }

    /** Animal valido mas cercano dentro del radio de caza, o null. */
    private Animal findPrey() {
        if (!PigAbominationConfig.HUNT_ANIMALS.get()) {
            return null;
        }
        if (level().getEntitiesOfClass(AnimalCorpseEntity.class, this.getBoundingBox().inflate(CORPSE_RADIUS))
                .size() >= MAX_CORPSES_NEAR) {
            return null;
        }
        List<Animal> animals = level().getEntitiesOfClass(Animal.class,
                this.getBoundingBox().inflate(HUNT_RANGE), this::isPrey);
        Animal best = null;
        double bestD = Double.MAX_VALUE;
        for (Animal a : animals) {
            double d = a.distanceToSqr(this);
            if (d < bestD) {
                bestD = d;
                best = a;
            }
        }
        return best;
    }

    /** Vacas, ovejas, pollos y cerdos normales. No se cazan animales atados ni con nombre (suelen ser de alguien). */
    private boolean isPrey(Animal animal) {
        return PreyKind.of(animal) != null && animal.isAlive() && !animal.isLeashed() && !animal.hasCustomName();
    }

    /** Mata a la presa sin botin ni experiencia (discard) y deja su cadaver en el mismo sitio. */
    private void killPrey() {
        PreyKind kind = PreyKind.of(prey);
        if (kind == null) {
            return;
        }
        if (level() instanceof ServerLevel sl) {
            AnimalCorpseEntity corpse = ModEntities.ANIMAL_CORPSE.get().create(sl);
            if (corpse != null) {
                corpse.moveTo(prey.getX(), prey.getY(), prey.getZ(), prey.getYRot(), 0.0F);
                corpse.setup(kind, AnimalRemainsEntity.corpseTicks());
                sl.addFreshEntity(corpse);
            }
        }
        level().playSound(null, prey.getX(), prey.getY(), prey.getZ(), kind.hurtSound(),
                SoundSource.HOSTILE, 1.0F, 0.7F);
        playCreatureSound(ModSounds.GRUNT.get(), 0.8F);
        prey.discard();
    }

    // ---- La pelea -----------------------------------------------------------------------------------------------

    /** Logica de la pelea, cada tick en el servidor: decide la accion y la ejecuta. */
    private void fightTick() {
        AbominationAction action = getAction();
        if (action == AbominationAction.EMERGE) {
            emergeTick();
            return;
        }
        if (attackCooldown > 0) {
            attackCooldown--;
        }
        if (chargeCooldown > 0) {
            chargeCooldown--;
        }
        LivingEntity target = this.getTarget();
        if (target != null && !target.isAlive()) {
            target = null;
        }
        switch (action) {
            case NONE -> chaseTick(target);
            case BITE -> biteTick(target);
            case CLAW -> clawTick(target);
            case CHARGE_WINDUP -> windupTick(target);
            case CHARGE -> chargeTick();
            default -> restTick(action);
        }
    }

    /** Persigue encorvada y decide el siguiente ataque. */
    private void chaseTick(LivingEntity target) {
        if (!enraged && this.getHealth() <= this.getMaxHealth() * PigAbominationConfig.ENRAGE_AT_HEALTH.get().floatValue()) {
            enraged = true;
            setAction(AbominationAction.ROAR);
            stopMoving();
            return;
        }
        if (target == null) {
            this.setSprinting(false);
            seekFarPlayer();
            return;
        }
        double dist = horizontalDistanceTo(target);
        this.getLookControl().setLookAt(target, 30.0F, 30.0F);
        boolean sees = this.hasLineOfSight(target);
        if (attackCooldown <= 0 && sees) {
            if (dist <= BITE_START) {
                setAction(this.random.nextInt(5) < 3 ? AbominationAction.BITE : AbominationAction.CLAW);
                return;
            }
            if (dist <= CLAW_START) {
                setAction(AbominationAction.CLAW);
                return;
            }
            if (chargeCooldown <= 0 && dist >= CHARGE_MIN && dist <= CHARGE_MAX) {
                chargeCooldown = PigAbominationConfig.ticks(enraged ? PigAbominationConfig.ENRAGED_CHARGE_COOLDOWN_SECONDS
                        : PigAbominationConfig.CHARGE_COOLDOWN_SECONDS) + this.random.nextInt(enraged ? 60 : 100);
                setAction(AbominationAction.CHARGE_WINDUP);
                return;
            }
        }
        // Corre en dos patas si esta lejos; camina encorvada si esta cerca.
        this.setSprinting(dist > 9.0D);
        double speed = enraged ? 1.2D : 1.0D;
        if (dist > 2.5D || !sees) {
            if (this.tickCount % 8 == 0 || this.getNavigation().isDone()) {
                this.getNavigation().moveTo(target, speed);
            }
        } else {
            this.getNavigation().stop();
        }
    }

    /** Sin objetivo a la vista: va hacia el jugador mas cercano aunque este lejos (hasta 160 bloques). */
    private void seekFarPlayer() {
        if (this.tickCount % 20 != 0) {
            return;
        }
        Player p = this.level().getNearestPlayer(this.getX(), this.getY(), this.getZ(), 160.0D,
                EntitySelector.NO_CREATIVE_OR_SPECTATOR);
        if (p == null) {
            return;
        }
        Vec3 dir = p.position().subtract(this.position());
        double len = dir.length();
        if (len < 1.0E-3D) {
            return;
        }
        Vec3 goal = this.position().add(dir.scale(Math.min(len, 28.0D) / len));
        this.getNavigation().moveTo(goal.x, goal.y, goal.z, 1.0D);
    }

    private void biteTick(LivingEntity target) {
        stopMoving();
        if (target != null && actionTicks < AbominationAction.BITE_STRIKE - DODGE_WINDOW) {
            faceTowards(target.getX(), target.getZ(), 30.0F);
        }
        if (actionTicks == 1) {
            playCreatureSound(ModSounds.GROWL.get(), 1.6F, 0.8F);
        }
        if (actionTicks == AbominationAction.BITE_STRIKE) {
            playCreatureSound(ModSounds.GRUNT.get(), 1.6F, 0.7F);
            for (Player p : playersInCone(BITE_REACH, BITE_CONE)) {
                hit(p, PigAbominationConfig.BITE_DAMAGE.get().floatValue(), 0.7D, 0.0D);
            }
        }
        if (actionTicks >= AbominationAction.BITE.duration) {
            endAction(meleeCooldown());
        }
    }

    private void clawTick(LivingEntity target) {
        stopMoving();
        if (target != null && actionTicks < AbominationAction.CLAW_STRIKE - DODGE_WINDOW) {
            faceTowards(target.getX(), target.getZ(), 30.0F);
        }
        if (actionTicks == 1) {
            playCreatureSound(ModSounds.GROWL.get(), 1.4F, 0.9F);
        }
        if (actionTicks == AbominationAction.CLAW_STRIKE) {
            playCreatureSound(ModSounds.GRUNT.get(), 1.4F, 1.0F);
            if (this.level() instanceof ServerLevel sl) {
                float yaw = this.getYRot() * (float) (Math.PI / 180.0D);
                sl.sendParticles(ParticleTypes.SWEEP_ATTACK, this.getX() - Mth.sin(yaw) * 1.8D, this.getY() + 1.3D,
                        this.getZ() + Mth.cos(yaw) * 1.8D, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
            for (Player p : playersInCone(CLAW_REACH, CLAW_CONE)) {
                hit(p, PigAbominationConfig.CLAW_DAMAGE.get().floatValue(), 1.0D, 0.15D);
            }
        }
        if (actionTicks >= AbominationAction.CLAW.duration) {
            endAction(meleeCooldown());
        }
    }

    /** Aviso de la embestida: se agacha, ruge y escarba el suelo. Deja de seguirte unos ticks antes de salir. */
    private void windupTick(LivingEntity target) {
        stopMoving();
        if (actionTicks == 1) {
            playCreatureSound(ModSounds.GROWL.get(), 2.0F, 0.7F);
        }
        if (target != null && actionTicks < AbominationAction.CHARGE_WINDUP.duration - 6) {
            faceTowards(target.getX(), target.getZ(), 20.0F);
        }
        if (actionTicks >= 12 && actionTicks % 4 == 0) {
            scratchGround();
        }
        if (actionTicks >= AbominationAction.CHARGE_WINDUP.duration) {
            float yaw = this.getYRot() * (float) (Math.PI / 180.0D);
            chargeDir = new Vec3(-Mth.sin(yaw), 0.0D, Mth.cos(yaw));
            setAction(AbominationAction.CHARGE);
            this.setSprinting(true);
            playCreatureSound(ModSounds.ROAR.get(), 1.6F, 1.1F);
        }
    }

    /** Embestida en linea recta: se puede esquivar a un lado. Si choca con un muro lo rompe y queda aturdida. */
    private void chargeTick() {
        faceTowards(this.getX() + chargeDir.x, this.getZ() + chargeDir.z, 90.0F);
        Vec3 motion = this.getDeltaMovement();
        double speed = (enraged ? PigAbominationConfig.ENRAGED_CHARGE_SPEED : PigAbominationConfig.CHARGE_SPEED).get();
        this.setDeltaMovement(chargeDir.x * speed, motion.y, chargeDir.z * speed);

        boolean hitAny = false;
        for (Player p : playersNear(1.9D)) {
            hit(p, PigAbominationConfig.CHARGE_DAMAGE.get().floatValue(), 1.7D, 0.4D);
            hitAny = true;
        }
        if (hitAny) {
            this.setSprinting(false);
            playCreatureSound(ModSounds.GRUNT.get(), 1.8F, 0.6F);
            setAction(AbominationAction.RECOVER);
            return;
        }
        if (this.horizontalCollision && actionTicks > 3) {
            smash();
            this.setSprinting(false);
            setAction(AbominationAction.STAGGER);
            return;
        }
        if (actionTicks >= AbominationAction.CHARGE.duration) {
            this.setSprinting(false);
            setAction(AbominationAction.RECOVER);
        }
    }

    /** Aturdida, recuperandose o rugiendo: quieta hasta que acaba la animacion. */
    private void restTick(AbominationAction action) {
        stopMoving();
        if (action == AbominationAction.ROAR && actionTicks == 1) {
            playCreatureSound(ModSounds.ROAR.get(), 2.5F, 0.9F);
        }
        if (action == AbominationAction.ROAR && actionTicks == 10 && this.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + 1.5D, this.getZ(),
                    40, 0.8D, 1.0D, 0.8D, 0.05D);
        }
        if (actionTicks >= action.duration) {
            endAction(action == AbominationAction.ROAR ? 20 : 30);
        }
    }

    private void endAction(int cooldown) {
        setAction(AbominationAction.NONE);
        this.setSprinting(false);
        this.attackCooldown = Math.max(this.attackCooldown, cooldown);
    }

    private int meleeCooldown() {
        return enraged ? 10 + this.random.nextInt(10) : 22 + this.random.nextInt(18);
    }

    /** Surgir tras la transformacion: invulnerable y sin barra, con humo, gruñidos y el rugido final. */
    private void emergeTick() {
        stopMoving();
        int t = actionTicks;
        if (this.level() instanceof ServerLevel sl) {
            if (t % 3 == 0) {
                sl.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + 0.2D, this.getZ(),
                        2, 0.6D, 0.1D, 0.6D, 0.02D);
            }
            if (t % 5 == 0 && t < AbominationAction.EMERGE_ROAR) {
                sl.sendParticles(ParticleTypes.ASH, this.getX(), this.getY() + 1.5D, this.getZ(),
                        6, 0.7D, 1.0D, 0.7D, 0.01D);
            }
            if (t == AbominationAction.EMERGE_ROAR) {
                sl.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + 1.5D, this.getZ(),
                        60, 1.0D, 1.2D, 1.0D, 0.06D);
            }
        }
        if (t == 2) {
            playCreatureSound(ModSounds.GROWL.get(), 1.8F, 0.6F);
        }
        if (t == 34) {
            playCreatureSound(ModSounds.GROWL.get(), 1.8F, 0.5F);
        }
        if (t == AbominationAction.EMERGE_ROAR) {
            playCreatureSound(ModSounds.ROAR.get(), 3.0F);
            for (Player p : this.level().players()) {
                if (p.distanceTo(this) < DARK_RANGE) {
                    p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 70, 0, false, false));
                }
            }
        }
        LivingEntity target = this.getTarget();
        if (target != null && t >= 50) {
            faceTowards(target.getX(), target.getZ(), 15.0F);
        }
        if (t >= AbominationAction.EMERGE.duration) {
            setAction(AbominationAction.NONE);
            this.bossEvent.setVisible(true);
        }
    }

    // ---- Ayudas de combate --------------------------------------------------------------------------------------

    private double horizontalDistanceTo(Entity e) {
        double dx = e.getX() - this.getX();
        double dz = e.getZ() - this.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private void stopMoving() {
        this.getNavigation().stop();
        Vec3 m = this.getDeltaMovement();
        this.setDeltaMovement(m.x * 0.3D, m.y, m.z * 0.3D);
    }

    /** Gira el cuerpo hacia un punto, como mucho maxTurn grados por tick. */
    private void faceTowards(double x, double z, float maxTurn) {
        float want = (float) (Mth.atan2(z - this.getZ(), x - this.getX()) * (180.0D / Math.PI)) - 90.0F;
        float diff = Mth.wrapDegrees(want - this.getYRot());
        float yaw = this.getYRot() + Mth.clamp(diff, -maxTurn, maxTurn);
        this.setYRot(yaw);
        this.yBodyRot = yaw;
        this.yHeadRot = yaw;
    }

    /** Jugadores a menos de range bloques (en horizontal) y dentro de un cono por delante de la criatura. */
    private List<Player> playersInCone(double range, double minDot) {
        float yaw = this.getYRot() * (float) (Math.PI / 180.0D);
        double fx = -Mth.sin(yaw);
        double fz = Mth.cos(yaw);
        List<Player> out = new ArrayList<>();
        for (Player p : playersNear(range)) {
            double dx = p.getX() - this.getX();
            double dz = p.getZ() - this.getZ();
            double len = Math.sqrt(dx * dx + dz * dz);
            double dot = len < 1.0E-3D ? 1.0D : (dx * fx + dz * fz) / len;
            if (dot >= minDot) {
                out.add(p);
            }
        }
        return out;
    }

    /** Jugadores a menos de range bloques en horizontal y a una altura alcanzable. */
    private List<Player> playersNear(double range) {
        List<Player> out = new ArrayList<>();
        AABB box = this.getBoundingBox().inflate(range + 1.0D, 2.0D, range + 1.0D);
        for (Player p : this.level().getEntitiesOfClass(Player.class, box)) {
            if (!p.isAlive() || p.isSpectator()) {
                continue;
            }
            double dy = p.getY() - this.getY();
            if (dy < -1.5D || dy > 3.0D) {
                continue;
            }
            if (horizontalDistanceTo(p) <= range + p.getBbWidth() * 0.5D) {
                out.add(p);
            }
        }
        return out;
    }

    /** Dano y empujon. La criatura aleja al jugador en la direccion en la que ella lo golpea. */
    private void hit(Player p, float damage, double knockback, double lift) {
        if (p.hurt(this.damageSources().mobAttack(this), damage)) {
            p.knockback(knockback, this.getX() - p.getX(), this.getZ() - p.getZ());
            if (lift > 0.0D) {
                p.setDeltaMovement(p.getDeltaMovement().add(0.0D, lift, 0.0D));
            }
            p.hurtMarked = true;
        }
    }

    /** Escarba el suelo: polvo del bloque que pisa y un golpe sordo. */
    private void scratchGround() {
        if (!(this.level() instanceof ServerLevel sl)) {
            return;
        }
        BlockState below = sl.getBlockState(this.blockPosition().below());
        if (!below.isAir()) {
            sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, below), this.getX(), this.getY() + 0.1D,
                    this.getZ(), 10, 0.5D, 0.1D, 0.5D, 0.08D);
        }
        playCreatureSound(SoundEvents.GRAVEL_BREAK, 1.5F, 0.6F);
    }

    /** Choque de la embestida contra un muro: rompe lo que le cierra el paso, humo y estruendo. */
    private void smash() {
        breakBlocksInWay();
        if (this.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX() + chargeDir.x, this.getY() + 1.0D,
                    this.getZ() + chargeDir.z, 25, 0.6D, 0.8D, 0.6D, 0.05D);
        }
        playCreatureSound(SoundEvents.GENERIC_EXPLODE, 1.2F, 0.7F);
    }

    /**
     * Durante la pelea, rompe los bloques que le cierran el paso (tres de alto, en la direccion en la que mira),
     * para que no se atasque. Solo si la regla mobGriefing esta activada.
     */
    private void breakBlocksInWay() {
        if (!(this.level() instanceof ServerLevel sl) || !this.horizontalCollision) {
            return;
        }
        if (!PigAbominationConfig.ALLOW_BLOCK_BREAKING.get()
                || !sl.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            return;
        }
        Vec3 look = this.getLookAngle();
        Direction dir = Direction.getNearest(look.x, 0.0D, look.z);
        BlockPos front = this.blockPosition().relative(dir);
        for (int dy = 0; dy <= 2; dy++) {
            BlockPos pos = front.above(dy);
            BlockState state = sl.getBlockState(pos);
            if (!state.isAir() && state.getDestroySpeed(sl, pos) >= 0.0F) {
                sl.destroyBlock(pos, false);
            }
        }
    }

    private void vanish() {
        if (this.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 1.0D, getZ(), 12, 0.4D, 0.6D, 0.4D, 0.02D);
        }
        this.discard();
    }

    // ---- Dano, muerte y final -----------------------------------------------------------------------------------

    @Override
    public boolean hurt(DamageSource source, float amount) {
        AbominationAction action = getAction();
        if (action == AbominationAction.EMERGE && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        }
        if (action == AbominationAction.STAGGER) {
            // Aturdida: es el momento de contraatacar.
            amount *= 1.5F;
        }
        return super.hurt(source, amount);
    }

    /** Grita al recibir un golpe (el audio "grito" del autor). */
    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.SCREAM.get();
    }

    /**
     * El grito dura 1,5 s y con una espada se golpea cada 0,6 s: para que los gritos no se amontonen, solo suena
     * uno cada 0,75 s como maximo. Suena mas alto que el sonido normal de una criatura.
     */
    @Override
    protected void playHurtSound(DamageSource source) {
        if (this.tickCount - this.lastScream < SCREAM_GAP) {
            return;
        }
        this.lastScream = this.tickCount;
        this.playSound(getHurtSound(source), PigAbominationConfig.SCREAM_VOLUME.get().floatValue(), this.getVoicePitch());
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.ROAR.get();
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!this.level().isClientSide && behavior == Behavior.FIGHT) {
            onDefeated();
        }
    }

    /** La pelea termina: la barra desaparece, se anota la victoria en el mundo y se avisa a todos. */
    private void onDefeated() {
        this.bossEvent.setVisible(false);
        this.bossEvent.removeAllPlayers();
        this.setSprinting(false);
        MinecraftServer server = this.level().getServer();
        if (server != null) {
            HorrorWorldData.get(server).markBossDefeated();
            server.getPlayerList().broadcastSystemMessage(Component.translatable("pigabomination.boss.defeated"), false);
        }
        if (this.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + 1.5D, this.getZ(),
                    80, 1.0D, 1.4D, 1.0D, 0.05D);
            sl.sendParticles(ParticleTypes.SOUL, this.getX(), this.getY() + 1.5D, this.getZ(),
                    30, 0.8D, 1.2D, 0.8D, 0.04D);
        }
    }

    /** Muerte lenta: cae y se queda tendida unos segundos antes de desvanecerse. */
    @Override
    protected void tickDeath() {
        ++this.deathTime;
        if (this.deathTime >= DEATH_TICKS && !this.level().isClientSide() && !this.isRemoved()) {
            this.level().broadcastEntityEvent(this, (byte) 60);
            this.remove(Entity.RemovalReason.KILLED);
        }
    }

    @Override
    public void tick() {
        super.tick();
        this.actionTicks++;
        if (this.level().isClientSide) {
            return;
        }
        if (isStalking()) {
            ambientSounds();
            finishStalkAction();
            return;
        }
        if (behavior != Behavior.FIGHT || this.isDeadOrDying()) {
            return;
        }
        fightSounds();
        fightTick();
        this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());
        if (this.tickCount % 10 == 0 && getAction() != AbominationAction.EMERGE) {
            updateBossBarViewers();
        }
    }

    /**
     * Quien ve la barra de vida: los jugadores de esta dimension a menos de BOSS_RANGE. Se quitan los que se alejan
     * (con margen para que no parpadee), los desconectados y los que cambian de dimension.
     */
    private void updateBossBarViewers() {
        for (ServerPlayer sp : new ArrayList<>(this.bossEvent.getPlayers())) {
            if (sp.isRemoved() || sp.level() != this.level() || sp.distanceTo(this) > BOSS_RANGE + 16.0D) {
                this.bossEvent.removePlayer(sp);
            }
        }
        for (Player p : this.level().players()) {
            if (p instanceof ServerPlayer sp && !sp.isRemoved() && sp.distanceTo(this) < BOSS_RANGE) {
                this.bossEvent.addPlayer(sp);
            }
        }
    }

    /** En el acecho, el arañazo de la caza termina solo cuando acaba su animacion. */
    private void finishStalkAction() {
        AbominationAction action = getAction();
        if (action != AbominationAction.NONE && actionTicks >= action.duration) {
            setAction(AbominationAction.NONE);
        }
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        super.remove(reason);
        this.bossEvent.removeAllPlayers();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("Behavior", behavior.name());
        tag.putInt("Lifetime", lifetime);
        tag.putInt("RetreatTicks", retreatTicks);
        tag.putBoolean("Hurts", hurts);
        tag.putBoolean("Enraged", enraged);
        // Solo el surgir se conserva al guardar: el resto de acciones empiezan de nuevo.
        if (getAction() == AbominationAction.EMERGE) {
            tag.putInt("EmergeTicks", actionTicks);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        behavior = parseBehavior(tag.getString("Behavior"));
        lifetime = tag.getInt("Lifetime");
        retreatTicks = tag.getInt("RetreatTicks");
        hurts = tag.getBoolean("Hurts");
        enraged = tag.getBoolean("Enraged");
        if (tag.contains("EmergeTicks")) {
            setAction(AbominationAction.EMERGE);
            actionTicks = tag.getInt("EmergeTicks");
        }
        this.bossEvent.setVisible(behavior == Behavior.FIGHT && getAction() != AbominationAction.EMERGE);
    }

    private static Behavior parseBehavior(String name) {
        for (Behavior b : Behavior.values()) {
            if (b.name().equals(name)) {
                return b;
            }
        }
        return Behavior.IDLE;
    }
}
