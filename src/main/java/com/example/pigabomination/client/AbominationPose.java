package com.example.pigabomination.client;

import com.example.pigabomination.entity.AbominationAction;

/**
 * Pose de la abominacion: los giros de cada hueso segun lo que hace.
 *
 * Es solo matematica (sin clases de Minecraft), para poder probarla fuera del juego: tools/preview_mesh.py dibuja
 * estos mismos giros sobre la malla. Los angulos van en radianes, en el espacio del modelo: eje Y hacia arriba,
 * la cara hacia -Z. Un giro en X positivo adelanta o levanta lo que esta por delante del hueso (la cabeza sube, una
 * pata que cuelga se balancea hacia delante, el cuerpo se alza sobre las patas traseras); un giro en X negativo lo
 * baja. En Z positivo, lo que cuelga del lado +X (la izquierda de la criatura) se abre hacia fuera. En Y gira.
 *
 * Es una bestia a cuatro patas, con el cuerpo encorvado y los brazos largos que llegan al suelo:
 *  - el reposo es el propio modelo (todos los giros a cero): las cuatro patas apoyadas;
 *  - camina con pasos cruzados (mano izquierda con pata derecha) y levanta cada pata doblando codo o corvejon;
 *  - los ataques la alzan sobre las patas traseras (el cuerpo gira alrededor de la pelvis) o la agachan.
 *
 * Esqueleto (el mismo orden que tools/bbmodel_to_mesh.py): "body" es el tronco y gira desde la pelvis, y arrastra
 * la cabeza, los brazos y la cola; las patas traseras cuelgan de la raiz y se quedan plantadas.
 */
public final class AbominationPose {
    /** Nombres de los huesos, en el mismo orden que el modelo (abomination_mesh.json). */
    public static final String[] BONES = {
            "body", "head", "jaw", "tail1", "tail2",
            "arm_l", "fore_l", "arm_r", "fore_r",
            "thigh_l", "shin_l", "thigh_r", "shin_r"
    };
    public static final int BODY = 0;
    public static final int HEAD = 1;
    public static final int JAW = 2;
    public static final int TAIL1 = 3;
    public static final int TAIL2 = 4;
    public static final int ARM_L = 5;
    public static final int FORE_L = 6;
    public static final int ARM_R = 7;
    public static final int FORE_R = 8;
    public static final int THIGH_L = 9;
    public static final int SHIN_L = 10;
    public static final int THIGH_R = 11;
    public static final int SHIN_R = 12;

    private static final float DEG = (float) Math.PI / 180.0F;

    /** Mandibula: el modelo la tiene muy abierta; en reposo se cierra casi del todo, un poco floja. */
    private static final float JAW_IDLE = 0.32F;
    /** Mandibula abierta del todo (rugido, aviso): un poco mas que en el modelo. */
    private static final float JAW_WIDE = -0.18F;

    /** Giros de cada hueso: rot[hueso] = {x, y, z}. */
    public final float[][] rot = new float[BONES.length][3];
    /** Desplazamiento de todo el cuerpo, en bloques (-Z es hacia delante): agacharse, lanzarse, saltar. */
    public float offsetX;
    public float offsetY;
    public float offsetZ;

    /**
     * @param action     accion actual
     * @param t          ticks desde que empezo la accion (con decimales)
     * @param limbSwing  fase de la marcha de vanilla
     * @param limbAmount intensidad de la marcha de vanilla (0 a 1)
     * @param age        edad de la entidad en ticks (con decimales), para la respiracion
     * @param headYawDeg  giro de la cabeza respecto al cuerpo, en grados
     * @param headPitchDeg inclinacion de la cabeza, en grados
     * @param sprinting  true si corre
     * @param hurt       reaccion al golpe, de 0 (nada) a 1 (recien golpeada)
     */
    public void update(AbominationAction action, float t, float limbSwing, float limbAmount, float age,
                       float headYawDeg, float headPitchDeg, boolean sprinting, float hurt) {
        clear();
        float lookY = -clamp(headYawDeg, -40.0F, 40.0F) * DEG;
        float lookX = -clamp(headPitchDeg, -30.0F, 30.0F) * DEG;

        switch (action) {
            case EMERGE -> emerge(t);
            case BITE -> bite(t, age);
            case CLAW -> claw(t, age);
            case CHARGE_WINDUP -> chargeWindup(t, age);
            case CHARGE -> charge(t);
            case STAGGER -> stagger(t, age);
            case RECOVER -> recover(t, age);
            case ROAR -> roar(t);
            default -> walk(limbSwing, limbAmount, age, sprinting);
        }

        // La mirada se suma a cualquier pose, salvo cuando la accion la controla del todo (surgir).
        float follow = action == AbominationAction.EMERGE ? 0.0F : 1.0F;
        rot[HEAD][0] += lookX * follow;
        rot[HEAD][1] += lookY * follow;
        // Reaccion al golpe: se echa hacia atras un momento, con la boca abierta.
        rot[BODY][0] += hurt * 0.16F;
        rot[HEAD][0] += hurt * 0.30F;
        rot[JAW][0] -= hurt * 0.45F;
    }

    private void clear() {
        for (float[] a : rot) {
            a[0] = 0.0F;
            a[1] = 0.0F;
            a[2] = 0.0F;
        }
        offsetX = 0.0F;
        offsetY = 0.0F;
        offsetZ = 0.0F;
    }

    private void set(int bone, float x, float y, float z) {
        rot[bone][0] = x;
        rot[bone][1] = y;
        rot[bone][2] = z;
    }

    /** Quieta, caminando (pasos cruzados) o corriendo. */
    private void walk(float limbSwing, float limbAmount, float age, boolean running) {
        float ph = limbSwing * (running ? 0.55F : 0.42F);
        float c = (float) Math.cos(ph);
        float s = (float) Math.sin(ph);
        float amt = limbAmount;
        float armAmp = running ? 0.50F : 0.30F;
        float legAmp = running ? 0.55F : 0.34F;
        float flex = running ? 0.85F : 0.60F;
        float breathe = (float) Math.sin(age * 0.07F) * 0.02F;
        float slack = 1.0F - amt;

        // Pasos cruzados: mano izquierda con pata derecha; mano derecha con pata izquierda.
        // Una pata se levanta (se dobla el codo o el corvejon) mientras avanza.
        float liftA = Math.max(0.0F, -s);   // mano izquierda y pata derecha
        float liftB = Math.max(0.0F, s);    // mano derecha y pata izquierda
        set(ARM_L, c * armAmp * amt, 0.0F, 0.04F);
        set(ARM_R, -c * armAmp * amt, 0.0F, -0.04F);
        set(FORE_L, -flex * liftA * amt, 0.0F, 0.0F);
        set(FORE_R, -flex * liftB * amt, 0.0F, 0.0F);
        set(THIGH_R, c * legAmp * amt, 0.0F, 0.0F);
        set(THIGH_L, -c * legAmp * amt, 0.0F, 0.0F);
        set(SHIN_R, -flex * 0.9F * liftA * amt, 0.0F, 0.0F);
        set(SHIN_L, -flex * 0.9F * liftB * amt, 0.0F, 0.0F);

        // El cuerpo se bambolea con cada paso y sube y baja con el doble de frecuencia; la cabeza lo compensa.
        float sway = s * (running ? 0.05F : 0.035F) * amt;
        float twist = c * (running ? 0.09F : 0.06F) * amt;
        set(BODY, breathe + (running ? -0.06F * amt : 0.0F), twist, sway);
        offsetY = -(running ? 0.05F : 0.03F) * amt * (0.5F + 0.5F * (float) Math.cos(ph * 2.0F));
        set(HEAD, 0.0F, -twist * 0.8F, -sway * 0.9F + (float) Math.sin(age * 0.045F) * 0.07F * slack);
        set(JAW, JAW_IDLE + (float) Math.sin(age * 0.09F) * 0.04F - (running ? 0.35F * amt : 0.0F), 0.0F, 0.0F);
        // La cola cuelga y se mece; al andar va en contra del cuerpo.
        float tailSway = (float) Math.sin(age * 0.06F) * 0.22F * slack - s * 0.30F * amt;
        set(TAIL1, (float) Math.sin(age * 0.045F) * 0.10F * slack - (running ? 0.35F * amt : 0.0F), 0.0F, tailSway);
        set(TAIL2, (float) Math.sin(age * 0.045F - 0.8F) * 0.16F * slack, 0.0F, tailSway * 1.4F);
    }

    /** Mordisco: echa la cabeza atras con la boca abierta y la lanza hacia delante, cerrando las fauces de golpe. */
    private void bite(float t, float age) {
        set(BODY, curve(t, 0, 0, 14, 0.20F, 17, -0.18F, 22, -0.14F, 34, 0), 0, 0);
        offsetZ = curve(t, 0, 0, 14, 0.10F, 17, -0.30F, 24, -0.22F, 34, 0);
        set(HEAD, curve(t, 0, 0, 14, 0.65F, 17, -0.45F, 24, -0.35F, 34, 0), 0, 0);
        set(JAW, curve(t, 0, JAW_IDLE, 12, JAW_WIDE - 0.20F, 16, JAW_WIDE - 0.25F, 18, 0.75F, 24, 0.60F, 34, JAW_IDLE),
                0, 0);
        // Las manos se afirman: se echan atras al cargar el golpe y se clavan al lanzarse.
        float arms = curve(t, 0, 0, 14, -0.30F, 17, 0.25F, 34, 0);
        set(ARM_L, arms, 0, curve(t, 0, 0.04F, 14, 0.25F, 34, 0.04F));
        set(ARM_R, arms, 0, -curve(t, 0, 0.04F, 14, 0.25F, 34, 0.04F));
        float legs = curve(t, 0, 0, 14, -0.22F, 17, 0.22F, 34, 0);
        set(THIGH_L, legs, 0, 0);
        set(THIGH_R, legs, 0, 0);
        float tail = (float) Math.sin(t * 0.5F) * 0.15F;
        set(TAIL1, curve(t, 0, 0, 17, -0.35F, 34, 0), 0, tail);
        set(TAIL2, 0, 0, tail * 1.4F);
    }

    /** Aranazo: se alza, levanta la garra derecha y barre de lado. */
    private void claw(float t, float age) {
        set(BODY, curve(t, 0, 0, 16, 0.42F, 20, 0.28F, 28, 0.24F, 38, 0),
                curve(t, 0, 0, 16, -0.40F, 21, 0.45F, 28, 0.35F, 38, 0), 0);
        offsetY = 0.0F;
        set(HEAD, curve(t, 0, 0, 16, 0.10F, 21, -0.10F, 38, 0), curve(t, 0, 0, 16, 0.30F, 21, -0.35F, 38, 0), 0);
        set(JAW, curve(t, 0, JAW_IDLE, 14, JAW_WIDE, 24, JAW_WIDE + 0.1F, 38, JAW_IDLE), 0, 0);
        set(ARM_R, curve(t, 0, 0, 16, 1.90F, 21, 1.30F, 28, 1.00F, 38, 0),
                curve(t, 0, 0, 16, 1.00F, 18, 1.00F, 22, -1.10F, 30, -0.90F, 38, 0),
                curve(t, 0, -0.04F, 16, -0.30F, 38, -0.04F));
        set(FORE_R, curve(t, 0, 0, 16, -0.45F, 21, -0.05F, 38, 0), 0, 0);
        // La otra mano se afirma en el suelo contra el empuje del cuerpo.
        set(ARM_L, curve(t, 0, 0, 16, -0.45F, 38, 0), 0, curve(t, 0, 0.04F, 16, 0.20F, 38, 0.04F));
        set(THIGH_L, curve(t, 0, 0, 16, -0.12F, 38, 0), 0, 0);
        set(THIGH_R, curve(t, 0, 0, 16, -0.12F, 38, 0), 0, 0);
        set(TAIL1, curve(t, 0, 0, 16, -0.25F, 38, 0), 0, (float) Math.sin(t * 0.4F) * 0.2F);
    }

    /** Aviso de la embestida: se agacha, sacude el cuerpo y escarba el suelo con las garras. */
    private void chargeWindup(float t, float age) {
        float down = ramp(t, 0.0F, 12.0F);
        float paw = (float) Math.sin(t * 0.8F) * 0.45F * ramp(t, 10.0F, 14.0F);
        set(BODY, curve(t, 0, 0, 12, -0.20F, 28, -0.20F), 0, (float) Math.sin(t * 2.1F) * 0.03F * down);
        offsetY = -0.14F * down;
        set(HEAD, curve(t, 0, 0, 10, -0.20F, 28, -0.28F), (float) Math.sin(t * 1.3F) * 0.12F * down, 0);
        set(JAW, curve(t, 0, JAW_IDLE, 10, JAW_WIDE, 28, JAW_WIDE), 0, 0);
        set(ARM_L, 0.10F * down + paw, 0, 0.04F + 0.10F * down);
        set(ARM_R, 0.10F * down - paw, 0, -0.04F - 0.10F * down);
        set(FORE_L, -0.35F * down * Math.max(0.0F, paw / 0.45F), 0, 0);
        set(FORE_R, -0.35F * down * Math.max(0.0F, -paw / 0.45F), 0, 0);
        // Las patas traseras se encogen para tomar impulso.
        set(THIGH_L, 0.30F * down, 0, 0);
        set(THIGH_R, 0.30F * down, 0, 0);
        set(SHIN_L, -0.45F * down, 0, 0);
        set(SHIN_R, -0.45F * down, 0, 0);
        float lash = (float) Math.sin(t * 0.9F) * 0.4F * down;
        set(TAIL1, 0.25F * down, 0, lash);
        set(TAIL2, 0.2F * down, 0, lash * 1.3F);
    }

    /** Embestida: galope, con las manos juntas y las patas traseras juntas, y el cuerpo estirado. */
    private void charge(float t) {
        float cyc = t * 0.85F;
        float c = (float) Math.cos(cyc);
        float s = (float) Math.sin(cyc);
        set(BODY, -0.10F + s * 0.10F, 0, 0);
        offsetY = Math.max(0.0F, s) * 0.10F - 0.04F;
        set(HEAD, -0.22F - s * 0.05F, 0, 0);
        set(JAW, JAW_WIDE, 0, 0);
        float armA = 0.30F + c * 0.65F;
        set(ARM_L, armA, 0, 0.08F);
        set(ARM_R, armA, 0, -0.08F);
        float liftArms = Math.max(0.0F, -s);
        set(FORE_L, -0.80F * liftArms, 0, 0);
        set(FORE_R, -0.80F * liftArms, 0, 0);
        float legA = (float) Math.cos(cyc - 1.8F) * 0.70F;
        set(THIGH_L, legA, 0, 0);
        set(THIGH_R, legA, 0, 0);
        float liftLegs = Math.max(0.0F, -(float) Math.sin(cyc - 1.8F));
        set(SHIN_L, -0.85F * liftLegs, 0, 0);
        set(SHIN_R, -0.85F * liftLegs, 0, 0);
        set(TAIL1, -0.75F, 0, 0);
        set(TAIL2, -0.40F, 0, 0);
    }

    /** Aturdida tras chocar: se yergue mareada y sacude la cabeza. */
    private void stagger(float t, float age) {
        float calm = 1.0F - ramp(t, 0.0F, 50.0F);
        set(BODY, curve(t, 0, -0.20F, 8, 0.55F, 20, 0.40F, 36, 0.15F, 50, 0), 0, (float) Math.sin(t * 0.7F) * 0.08F * calm);
        offsetY = 0.0F;
        set(HEAD, curve(t, 0, -0.20F, 8, 0.20F, 50, 0), (float) Math.sin(t * 0.9F) * 0.5F * calm, 0);
        set(JAW, curve(t, 0, JAW_WIDE, 14, JAW_WIDE + 0.1F, 50, JAW_IDLE), 0, 0);
        float arms = curve(t, 0, 0.30F, 8, 0.10F, 50, 0);
        set(ARM_L, arms, 0, 0.04F + 0.15F * calm);
        set(ARM_R, arms, 0, -0.04F - 0.15F * calm);
        set(FORE_L, -0.25F * calm, 0, 0);
        set(FORE_R, -0.25F * calm, 0, 0);
        set(THIGH_L, curve(t, 0, 0.2F, 8, -0.10F, 50, 0), 0, 0);
        set(THIGH_R, curve(t, 0, 0.2F, 8, -0.10F, 50, 0), 0, 0);
        set(TAIL1, 0, 0, (float) Math.sin(t * 0.5F) * 0.25F * calm);
    }

    /** Recuperacion: respira hondo y vuelve a apoyar las manos. */
    private void recover(float t, float age) {
        set(BODY, curve(t, 0, 0.35F, 10, 0.15F, 26, 0) + (float) Math.sin(t * 0.9F) * 0.04F, 0, 0);
        set(HEAD, curve(t, 0, 0.15F, 26, 0), 0, 0);
        set(JAW, curve(t, 0, JAW_WIDE + 0.1F, 26, JAW_IDLE) + (float) Math.sin(t * 0.9F) * 0.05F, 0, 0);
        float arms = curve(t, 0, 0.15F, 26, 0);
        set(ARM_L, arms, 0, 0.04F + 0.10F * (1.0F - ramp(t, 0, 26)));
        set(ARM_R, arms, 0, -0.04F - 0.10F * (1.0F - ramp(t, 0, 26)));
    }

    /** Rugido: se alza sobre las patas traseras con la cabeza alta y los brazos abiertos, temblando. */
    private void roar(float t) {
        float shake = (float) Math.sin(t * 2.4F) * 0.03F * ramp(t, 8.0F, 12.0F) * (1.0F - ramp(t, 40.0F, 50.0F));
        set(BODY, curve(t, 0, 0, 10, 0.55F, 40, 0.55F, 50, 0), 0, shake);
        set(HEAD, curve(t, 0, 0, 10, 0.45F, 40, 0.45F, 50, 0), 0, 0);
        set(JAW, curve(t, 0, JAW_IDLE, 10, JAW_WIDE - 0.2F, 40, JAW_WIDE - 0.2F, 50, JAW_IDLE), 0, 0);
        float arms = curve(t, 0, 0, 10, 0.95F, 40, 0.95F, 50, 0);
        float out = curve(t, 0, 0.04F, 10, 0.80F, 40, 0.80F, 50, 0.04F);
        set(ARM_L, arms, 0, out);
        set(ARM_R, arms, 0, -out);
        float fore = curve(t, 0, 0, 10, -0.30F, 40, -0.30F, 50, 0);
        set(FORE_L, fore, 0, 0);
        set(FORE_R, fore, 0, 0);
        set(THIGH_L, curve(t, 0, 0, 10, -0.25F, 40, -0.25F, 50, 0), 0, 0);
        set(THIGH_R, curve(t, 0, 0, 10, -0.25F, 40, -0.25F, 50, 0), 0, 0);
        set(TAIL1, curve(t, 0, 0, 10, -0.35F, 40, -0.35F, 50, 0), 0, shake * 6.0F);
    }

    /** Surgir: encogida y temblando, con la cabeza torcida; se alza de golpe sobre las patas traseras y ruge. */
    private void emerge(float t) {
        float tremble = 1.0F - ramp(t, 0.0F, 60.0F);
        float shake = (float) Math.sin(t * 1.9F) * (0.03F + 0.07F * tremble);
        set(BODY, curve(t, 0, -0.30F, 50, -0.25F, 64, -0.05F, 68, 0.55F, 74, 0.55F, 80, 0), 0, shake);
        offsetY = curve(t, 0, -0.30F, 50, -0.28F, 64, -0.10F, 68, 0, 80, 0);
        set(HEAD, curve(t, 0, -0.30F, 40, 0.10F, 62, 0.30F, 68, 0.45F, 74, 0.45F, 80, 0),
                (float) Math.sin(t * 0.5F) * 0.25F * (1.0F - ramp(t, 50.0F, 64.0F)),
                curve(t, 0, 0.50F, 50, 0.40F, 62, -0.15F, 68, 0, 80, 0));
        set(JAW, curve(t, 0, JAW_IDLE, 62, JAW_IDLE, 68, JAW_WIDE - 0.2F, 74, JAW_WIDE - 0.2F, 80, JAW_IDLE), 0, 0);
        float arms = curve(t, 0, -0.45F, 50, -0.35F, 64, 0.30F, 68, 0.95F, 74, 0.95F, 80, 0);
        float out = curve(t, 0, 0.10F, 64, 0.40F, 68, 0.80F, 74, 0.80F, 80, 0.04F);
        set(ARM_L, arms, 0, out);
        set(ARM_R, arms, 0, -out);
        float fore = curve(t, 0, -0.9F, 50, -0.8F, 64, -0.3F, 68, -0.3F, 80, 0);
        set(FORE_L, fore, 0, 0);
        set(FORE_R, fore, 0, 0);
        // Las patas traseras, dobladas bajo el cuerpo, se estiran al alzarse.
        float thigh = curve(t, 0, 0.55F, 50, 0.50F, 64, 0.20F, 68, -0.25F, 74, -0.25F, 80, 0);
        float shin = curve(t, 0, -0.80F, 50, -0.70F, 64, -0.30F, 68, 0, 80, 0);
        set(THIGH_L, thigh, 0, 0);
        set(THIGH_R, thigh, 0, 0);
        set(SHIN_L, shin, 0, 0);
        set(SHIN_R, shin, 0, 0);
        set(TAIL1, curve(t, 0, 0.3F, 64, 0.3F, 68, -0.35F, 80, 0), 0, shake * 5.0F);
    }

    /** Valor en t de una curva de claves (tiempo, valor, tiempo, valor, ...), suavizada entre claves. */
    private static float curve(float t, float... keys) {
        if (t <= keys[0]) {
            return keys[1];
        }
        for (int i = 2; i < keys.length; i += 2) {
            if (t <= keys[i]) {
                float t0 = keys[i - 2];
                float t1 = keys[i];
                float k = (t - t0) / (t1 - t0);
                k = k * k * (3.0F - 2.0F * k);
                return keys[i - 1] + (keys[i + 1] - keys[i - 1]) * k;
            }
        }
        return keys[keys.length - 1];
    }

    /** 0 antes de t0, 1 despues de t1, y una subida suave entre los dos. */
    private static float ramp(float t, float t0, float t1) {
        float k = clamp((t - t0) / (t1 - t0), 0.0F, 1.0F);
        return k * k * (3.0F - 2.0F * k);
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : Math.min(v, hi);
    }
}
