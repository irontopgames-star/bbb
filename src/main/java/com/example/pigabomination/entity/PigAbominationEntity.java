package com.example.pigabomination.entity;

import com.example.pigabomination.config.PigAbominationConfig;
import com.example.pigabomination.progression.HorrorDirector;
import com.example.pigabomination.registry.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.UUID;

/**
 * La criatura. Dos modos:
 * IDLE: el comportamiento de la Fase 1 (pasea y te mira), por ejemplo si sale del huevo.
 * Encuentros (resto de estados): acecho controlado por el servidor. Nunca ataca al jugador.
 * TRANSFORM (Fase 7): la transformacion en la abominacion. Dura unos 5 segundos: silencio, el cerdo tiembla y crece,
 * humo y ceniza, una voz que dice "voy a matarte", oscuridad, y al final HorrorDirector la sustituye por el jefe.
 */
public class PigAbominationEntity extends Monster {

    public enum Behavior { IDLE, WATCHING, WINDOW_STARE, DOOR_STARE, STALKING, RETREAT, DISAPPEAR, TRANSFORM }

    /** Duracion de la transformacion (5 s). */
    public static final int TRANSFORM_TICKS = 100;
    /** Instante (tick) de la voz "voy a matarte" durante la transformacion (el volumen esta en la configuracion). */
    private static final int THREAT_TICK = 20;
    /** A esta distancia (bloques) se oye el cambio y se ve como se oscurece. */
    private static final double TRANSFORM_RANGE = 40.0D;

    private static final EntityDataAccessor<Boolean> DATA_TRANSFORMING =
            SynchedEntityData.defineId(PigAbominationEntity.class, EntityDataSerializers.BOOLEAN);

    /** Si el jugador se acerca a menos de esta distancia, la criatura se retira. */
    private static final double CLOSE_RANGE = 6.0D;
    private static final int RETREAT_TICKS = 80;

    private Behavior behavior = Behavior.IDLE;
    private int lifetime;
    private int retreatTicks;
    /** Cuenta atras hasta el siguiente gruñido mientras te mira. */
    private int soundTimer = 100;
    /** Ticks de transformacion, contados en el servidor y en el cliente (para animar el temblor). */
    private int transformTicks;
    /** Jugador al que se enfrenta el jefe: sale de la transformacion mirandolo. */
    private UUID transformTarget;

    public PigAbominationEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, PigAbominationConfig.PIG_HEALTH.get())
                .add(Attributes.MOVEMENT_SPEED, PigAbominationConfig.PIG_SPEED.get());
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_TRANSFORMING, false);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_TRANSFORMING.equals(key)) {
            this.transformTicks = 0;
        }
    }

    /** El cerdo de dia no lo borra la dificultad pacifica ni la distancia: lo retira su propio encuentro. */
    @Override
    public boolean shouldDespawnInPeaceful() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToPlayer) {
        return false;
    }

    /** Activa un encuentro: el estado y cuantos ticks dura antes de retirarse. */
    public void setEncounter(Behavior newBehavior, int lifetimeTicks) {
        this.behavior = newBehavior;
        this.lifetime = lifetimeTicks;
        this.retreatTicks = 0;
    }

    public Behavior getBehavior() {
        return behavior;
    }

    public boolean isTransforming() {
        return this.entityData.get(DATA_TRANSFORMING);
    }

    /** Avance de la transformacion de 0 a 1, con decimales para que el temblor sea suave. */
    public float getTransformProgress(float partialTick) {
        return Mth.clamp((this.transformTicks + partialTick) / TRANSFORM_TICKS, 0.0F, 1.0F);
    }

    /** Empieza la transformacion: el cerdo se queda quieto, mirandote, e invulnerable hasta el final. */
    public void beginTransformation(Player target) {
        this.behavior = Behavior.TRANSFORM;
        this.transformTarget = target.getUUID();
        this.transformTicks = 0;
        this.lifetime = 0;
        this.setInvulnerable(true);
        this.setPersistenceRequired();
        this.getNavigation().stop();
        this.entityData.set(DATA_TRANSFORMING, true);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.isTransforming()) {
            this.transformTicks++;
            if (this.level() instanceof ServerLevel sl) {
                transformTick(sl);
            }
            return;
        }
        if (!this.level().isClientSide && (behavior == Behavior.WATCHING || behavior == Behavior.STALKING)
                && --soundTimer <= 0) {
            soundTimer = 200 + this.random.nextInt(200);
            this.level().playSound(null, getX(), getY(), getZ(), ModSounds.GRUNT.get(),
                    SoundSource.HOSTILE, 0.7F, 1.0F);
        }
    }

    /**
     * La transformacion, tick a tick (100 ticks):
     *   0-20   silencio: el cerdo se queda quieto y te mira.
     *   20     rompe el silencio con la voz: "voy a matarte" (dura unos 1,9 s, hasta el tick 57). Empieza a temblar.
     *   40     humo y ceniza.
     *   60     gruñido muy grave; el cuerpo se hincha, con esporas de color sangre.
     *   70     el mundo se oscurece para quien esta cerca; chillido deformado.
     *   80     rugido de la criatura.
     *   100    desaparece el cerdo y surge la abominacion (HorrorDirector.finishTransformation).
     * Los sonidos van separados a proposito: el gruñido del cerdo tiene tres pulsos y taparia la voz.
     */
    private void transformTick(ServerLevel sl) {
        int t = this.transformTicks;
        this.getNavigation().stop();
        if (t <= TRANSFORM_TICKS) {
            double spread = 0.3D + 0.4D * t / TRANSFORM_TICKS;
            if (t >= 15 && t % 4 == 0) {
                sl.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 0.6D, getZ(),
                        1 + t / 25, spread, 0.3D, spread, 0.01D);
            }
            if (t >= 40 && t % 5 == 0) {
                sl.sendParticles(ParticleTypes.ASH, getX(), getY() + 1.0D, getZ(),
                        5, 0.6D, 0.8D, 0.6D, 0.01D);
            }
            if (t >= 60 && t % 6 == 0) {
                sl.sendParticles(ParticleTypes.CRIMSON_SPORE, getX(), getY() + 0.8D, getZ(),
                        8, 0.7D, 0.6D, 0.7D, 0.02D);
            }
            if (t == 60) {
                sound(ModSounds.GRUNT.get(), 1.2F, 0.6F);
            }
            if (t == THREAT_TICK) {
                sound(ModSounds.THREAT.get(), PigAbominationConfig.THREAT_VOLUME.get().floatValue(), 1.0F);
            }
            if (t == 70) {
                sound(SoundEvents.PIG_HURT, 1.2F, 0.4F);
                for (Player p : sl.players()) {
                    if (p.distanceTo(this) < TRANSFORM_RANGE) {
                        p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 120, 0, false, false));
                    }
                }
            }
            if (t == 80) {
                sound(ModSounds.GROWL.get(), 1.8F, 0.6F);
            }
            if (t == 92) {
                sl.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 0.8D, getZ(),
                        30, 0.8D, 0.6D, 0.8D, 0.04D);
            }
        }
        // Al terminar, el director pone al jefe. Si no encuentra sitio o no hay jugadores, lo reintenta cada segundo.
        if (t >= TRANSFORM_TICKS && t % 20 == 0) {
            Player target = this.transformTarget == null ? null : sl.getPlayerByUUID(this.transformTarget);
            if (target == null) {
                target = sl.getNearestPlayer(this, 128.0D);
            }
            if (target != null) {
                HorrorDirector.finishTransformation(sl, this, target);
            }
        }
    }

    private void sound(SoundEvent sound, float volume, float pitch) {
        this.level().playSound(null, getX(), getY(), getZ(), sound, SoundSource.HOSTILE, volume, pitch);
    }

    @Override
    protected void registerGoals() {
        // Prioridad 0: el encuentro manda sobre todo lo demas (movimiento y mirada).
        this.goalSelector.addGoal(0, new EncounterGoal());
        this.goalSelector.addGoal(1, new FloatGoal(this));
        // Los paseos de la Fase 1 solo funcionan en modo IDLE.
        this.goalSelector.addGoal(2, new RandomStrollGoal(this, 0.6D, 40) {
            @Override
            public boolean canUse() {
                return behavior == Behavior.IDLE && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return behavior == Behavior.IDLE && super.canContinueToUse();
            }
        });
        this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 12.0F) {
            @Override
            public boolean canUse() {
                return behavior == Behavior.IDLE && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return behavior == Behavior.IDLE && super.canContinueToUse();
            }
        });
        this.goalSelector.addGoal(4, new RandomLookAroundGoal(this) {
            @Override
            public boolean canUse() {
                return behavior == Behavior.IDLE && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return behavior == Behavior.IDLE && super.canContinueToUse();
            }
        });
    }

    /** Toda la logica de los encuentros corre aqui, en el servidor. */
    private final class EncounterGoal extends Goal {
        EncounterGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return behavior != Behavior.IDLE;
        }

        @Override
        public boolean canContinueToUse() {
            return behavior != Behavior.IDLE;
        }

        @Override
        public void start() {
            getNavigation().stop();
        }

        @Override
        public void tick() {
            encounterTick();
        }
    }

    private void encounterTick() {
        if (behavior == Behavior.TRANSFORM) {
            // La transformacion la lleva tick(); aqui solo se queda quieto mirando al jugador.
            Player target = this.transformTarget == null ? null : this.level().getPlayerByUUID(this.transformTarget);
            this.getNavigation().stop();
            if (target != null) {
                this.getLookControl().setLookAt(target, 30.0F, 30.0F);
            }
            return;
        }
        Player p = this.level().getNearestPlayer(this, 40.0D);
        if (p == null) {
            vanish();
            return;
        }
        if (behavior != Behavior.RETREAT && behavior != Behavior.DISAPPEAR) {
            lifetime--;
            if (lifetime <= 0) {
                startRetreat();
            }
        }
        double d = this.distanceTo(p);
        switch (behavior) {
            case WATCHING, WINDOW_STARE, DOOR_STARE -> {
                if (d < CLOSE_RANGE) {
                    startRetreat();
                } else {
                    stareAt(p);
                }
            }
            case STALKING -> {
                if (d < CLOSE_RANGE) {
                    startRetreat();
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
                // IDLE: lo gestionan los goals de la Fase 1
            }
        }
    }

    private void stareAt(Player p) {
        this.getNavigation().stop();
        this.getLookControl().setLookAt(p, 30.0F, 30.0F);
    }

    private void startRetreat() {
        behavior = Behavior.RETREAT;
        retreatTicks = RETREAT_TICKS;
    }

    private void moveAway(Player p, double speed) {
        Vec3 away = this.position().subtract(p.position());
        if (away.lengthSqr() < 1.0E-4D) {
            away = new Vec3(1.0D, 0.0D, 0.0D);
        }
        Vec3 target = this.position().add(away.normalize().scale(10.0D));
        this.getNavigation().moveTo(target.x, target.y, target.z, speed);
    }

    private void vanish() {
        this.level().playSound(null, getX(), getY(), getZ(), SoundEvents.PIG_HURT, SoundSource.HOSTILE, 0.8F, 0.6F);
        if (this.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 1.0D, getZ(), 12, 0.4D, 0.6D, 0.4D, 0.02D);
        }
        this.discard();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("Behavior", behavior.name());
        tag.putInt("Lifetime", lifetime);
        tag.putInt("RetreatTicks", retreatTicks);
        if (behavior == Behavior.TRANSFORM) {
            tag.putInt("TransformTicks", transformTicks);
            if (transformTarget != null) {
                tag.putUUID("TransformTarget", transformTarget);
            }
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        behavior = parseBehavior(tag.getString("Behavior"));
        lifetime = tag.getInt("Lifetime");
        retreatTicks = tag.getInt("RetreatTicks");
        if (behavior == Behavior.TRANSFORM) {
            // Primero el dato sincronizado (al cambiar reinicia la cuenta) y despues los ticks guardados.
            this.entityData.set(DATA_TRANSFORMING, true);
            transformTicks = tag.getInt("TransformTicks");
            transformTarget = tag.hasUUID("TransformTarget") ? tag.getUUID("TransformTarget") : null;
        }
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
