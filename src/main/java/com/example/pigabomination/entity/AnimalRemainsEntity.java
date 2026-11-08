package com.example.pigabomination.entity;

import com.example.pigabomination.config.PigAbominationConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * Restos de un animal (cadaver o cabeza). No tiene vida, no se puede golpear ni recoger y desaparece
 * cuando se acaba su tiempo. El tipo de animal se sincroniza con el cliente para dibujar el correcto.
 */
public abstract class AnimalRemainsEntity extends Entity {
    /** Ticks que dura un cadaver de las noches 3 y 4 (config remains.corpseSeconds, 2 minutos por defecto). */
    public static int corpseTicks() {
        return PigAbominationConfig.ticks(PigAbominationConfig.CORPSE_SECONDS);
    }

    /** Ticks que dura una cabeza de la noche 4 (config remains.headSeconds, 2 minutos por defecto). */
    public static int headTicks() {
        return PigAbominationConfig.ticks(PigAbominationConfig.HEAD_SECONDS);
    }

    private static final EntityDataAccessor<Integer> DATA_KIND =
            SynchedEntityData.defineId(AnimalRemainsEntity.class, EntityDataSerializers.INT);
    /** true: las moscas solo se ven de dia (restos de la noche 1, que se descubren al amanecer). */
    private static final EntityDataAccessor<Boolean> DATA_DAWN_FLIES =
            SynchedEntityData.defineId(AnimalRemainsEntity.class, EntityDataSerializers.BOOLEAN);

    private int lifetime;

    protected AnimalRemainsEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.setInvulnerable(true);
    }

    public PreyKind getKind() {
        PreyKind[] kinds = PreyKind.values();
        return kinds[Math.floorMod(this.entityData.get(DATA_KIND), kinds.length)];
    }

    public boolean isDawnFlies() {
        return this.entityData.get(DATA_DAWN_FLIES);
    }

    /** Si el renderizador dibuja moscas sobre este resto. Solo los cadaveres las tienen. */
    public boolean hasFlies() {
        return false;
    }

    /** Fija el animal que representa y cuantos ticks sigue en el mundo. */
    public void setup(PreyKind kind, int lifeTicks) {
        setup(kind, lifeTicks, false);
    }

    public void setup(PreyKind kind, int lifeTicks, boolean dawnFlies) {
        this.entityData.set(DATA_KIND, kind.ordinal());
        this.entityData.set(DATA_DAWN_FLIES, dawnFlies);
        this.lifetime = lifeTicks;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_KIND, 0);
        this.entityData.define(DATA_DAWN_FLIES, false);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide && --this.lifetime <= 0) {
            this.discard();
        }
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        this.entityData.set(DATA_KIND, tag.getInt("Kind"));
        this.entityData.set(DATA_DAWN_FLIES, tag.getBoolean("DawnFlies"));
        this.lifetime = tag.getInt("Lifetime");
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Kind", this.entityData.get(DATA_KIND));
        tag.putBoolean("DawnFlies", this.entityData.get(DATA_DAWN_FLIES));
        tag.putInt("Lifetime", this.lifetime);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }
}
