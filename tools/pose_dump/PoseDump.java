import com.example.pigabomination.client.AbominationPose;
import com.example.pigabomination.entity.AbominationAction;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Calcula con el codigo REAL del mod (AbominationPose) los giros de cada hueso en varios momentos de cada accion,
 * y los escribe como JSON para tools/preview_mesh.py (opcion --angles). No necesita Minecraft:
 *
 *   javac -d /tmp/pose_out src/main/java/com/example/pigabomination/entity/AbominationAction.java \
 *         src/main/java/com/example/pigabomination/client/AbominationPose.java tools/pose_dump/PoseDump.java
 *   java -cp /tmp/pose_out PoseDump > poses.json
 */
public final class PoseDump {
    public static void main(String[] args) {
        Map<String, Object[]> s = new LinkedHashMap<>();
        // nombre -> {accion, t, limbSwing, limbAmount, correr}
        s.put("idle", new Object[]{AbominationAction.NONE, 0f, 0f, 0f, false});
        // Marcha: cuatro momentos del ciclo (fase 0, 1/4, 1/2 y 3/4 de vuelta) al andar y al correr.
        float walkTurn = (float) (2 * Math.PI / 0.42);
        float runTurn = (float) (2 * Math.PI / 0.55);
        for (int i = 0; i < 4; i++) {
            s.put("walk_" + i, new Object[]{AbominationAction.NONE, 0f, walkTurn * i / 4f, 1f, false});
            s.put("run_" + i, new Object[]{AbominationAction.NONE, 0f, runTurn * i / 4f, 1f, true});
        }
        for (int t : new int[]{8, 14, 16, 18, 24}) {
            s.put("bite_" + t, new Object[]{AbominationAction.BITE, (float) t, 0f, 0f, false});
        }
        for (int t : new int[]{8, 16, 19, 22, 30}) {
            s.put("claw_" + t, new Object[]{AbominationAction.CLAW, (float) t, 0f, 0f, false});
        }
        for (int t : new int[]{6, 14, 22}) {
            s.put("windup_" + t, new Object[]{AbominationAction.CHARGE_WINDUP, (float) t, 0f, 0f, false});
        }
        for (int t : new int[]{0, 2, 4, 6}) {
            s.put("charge_" + t, new Object[]{AbominationAction.CHARGE, (float) t, 0f, 0f, false});
        }
        for (int t : new int[]{4, 14, 30}) {
            s.put("stagger_" + t, new Object[]{AbominationAction.STAGGER, (float) t, 0f, 0f, false});
        }
        s.put("recover_10", new Object[]{AbominationAction.RECOVER, 10f, 0f, 0f, false});
        for (int t : new int[]{10, 25, 45}) {
            s.put("roar_" + t, new Object[]{AbominationAction.ROAR, (float) t, 0f, 0f, false});
        }
        for (int t : new int[]{0, 30, 56, 66, 72, 79}) {
            s.put("emerge_" + t, new Object[]{AbominationAction.EMERGE, (float) t, 0f, 0f, false});
        }
        s.put("hurt", new Object[]{AbominationAction.NONE, 0f, 0f, 0f, false, 1f});

        StringBuilder out = new StringBuilder("{\n");
        boolean first = true;
        for (Map.Entry<String, Object[]> e : s.entrySet()) {
            Object[] a = e.getValue();
            AbominationPose p = new AbominationPose();
            float hurt = a.length > 5 ? (Float) a[5] : 0f;
            p.update((AbominationAction) a[0], (Float) a[1], (Float) a[2], (Float) a[3], 0f, 0f, 0f, (Boolean) a[4], hurt);
            if (!first) {
                out.append(",\n");
            }
            first = false;
            out.append("\"").append(e.getKey()).append("\":{\"rot\":{");
            for (int i = 0; i < AbominationPose.BONES.length; i++) {
                out.append(i == 0 ? "" : ",").append("\"").append(AbominationPose.BONES[i]).append("\":").append(arr(p.rot[i]));
            }
            out.append(String.format(Locale.ROOT, "},\"offset\":[%.4f,%.4f,%.4f]}", p.offsetX, p.offsetY, p.offsetZ));
        }
        out.append("\n}\n");
        System.out.print(out);
    }

    private static String arr(float[] a) {
        return String.format(Locale.ROOT, "[%.4f,%.4f,%.4f]", a[0], a[1], a[2]);
    }
}
