package com.example.pigabomination.entity;

/**
 * Lo que hace la abominacion en cada momento de la pelea. El servidor decide la accion y la sincroniza con los
 * clientes; el cliente cuenta los ticks desde que cambia y anima el cuerpo con ellos (ver AbominationPose).
 * Por eso las duraciones y los instantes de los golpes estan aqui: los comparten el servidor y la animacion.
 */
public enum AbominationAction {
    /** Andar, correr en dos patas o quedarse quieta. */
    NONE(0),
    /** Surgir tras la transformacion: encogida, se incorpora y ruge. Dura lo que la cuenta de la animacion. */
    EMERGE(80),
    /** Mordisco: echa la cabeza atras con la boca abierta y la lanza hacia delante. */
    BITE(34),
    /** Arañazo: levanta un brazo y barre de lado con las garras. */
    CLAW(38),
    /** Aviso de la embestida: se agacha a cuatro patas y escarba el suelo. */
    CHARGE_WINDUP(28),
    /** Embestida: carrera en cuatro patas. Dura como maximo esto; acaba antes si choca. */
    CHARGE(40),
    /** Aturdida tras chocar contra un muro: es el momento de contraatacar. */
    STAGGER(50),
    /** Recuperacion tras una embestida que acerto o falló. */
    RECOVER(26),
    /** Rugido al enfurecerse (a mitad de vida). */
    ROAR(50);

    /** Tick del mordisco en el que cae el golpe. */
    public static final int BITE_STRIKE = 16;
    /** Tick del arañazo en el que cae el golpe. */
    public static final int CLAW_STRIKE = 19;
    /** Tick de EMERGE en el que suena el rugido. */
    public static final int EMERGE_ROAR = 62;

    public final int duration;

    AbominationAction(int duration) {
        this.duration = duration;
    }

    public static AbominationAction byId(int id) {
        AbominationAction[] all = values();
        return id >= 0 && id < all.length ? all[id] : NONE;
    }
}
