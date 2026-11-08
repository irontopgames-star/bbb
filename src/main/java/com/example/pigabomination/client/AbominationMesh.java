package com.example.pigabomination.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Malla de la abominacion (la de tu .bbmodel, con su textura), con esqueleto.
 * Se genera con tools/bbmodel_to_mesh.py.
 *
 * Es UNA sola piel. Cada vertice sigue a hasta tres huesos, con un peso para cada uno ("skinning"): asi los codos,
 * las rodillas, los hombros y la mandibula se doblan sin abrir grietas, cosa que pasaba al trocear la malla en piezas
 * rigidas. Cada fotograma se calculan las matrices de los huesos con los giros de AbominationPose, se mueve cada
 * vertice una sola vez y se dibujan los triangulos.
 *
 * Coordenadas en bloques: los pies en y = 0, el eje Y hacia arriba y la cara hacia -Z.
 */
public final class AbominationMesh {
    private static final String PATH = "/assets/pigabomination/models/abomination_mesh.json";
    /** Luz maxima (cielo y bloque a 15): para los ojos, que brillan en la oscuridad. */
    private static final int FULL_BRIGHT = 0xF000F0;
    /** Por vertice: x, y, z y tres pares (hueso, peso). */
    private static final int VERT_STRIDE = 9;
    private static final int INFLUENCES = 3;
    /** Por triangulo: tres indices de vertice, tres pares (u, v) y 1 si brilla. */
    private static final int TRI_STRIDE = 10;
    private static AbominationMesh instance;

    private final String[] boneNames;
    private final int[] boneParent;
    /** Articulacion de cada hueso: 3 valores por hueso. */
    private final float[] pivot;
    /** Posicion de cada vertice en reposo: 3 valores por vertice. */
    private final float[] rest;
    /** Los tres huesos que mueven cada vertice, y cuanto cada uno. */
    private final int[] influenceBone;
    private final float[] influenceWeight;
    private final int[] triVert;
    private final float[] triUv;
    private final boolean[] triGlow;

    /** Memoria de trabajo que se reutiliza en cada fotograma (solo se usa desde el hilo de dibujo). */
    private final float[] boneMat;
    private final float[] skinned;
    private final Vector3f normal = new Vector3f();

    private AbominationMesh(String[] boneNames, int[] boneParent, float[] pivot, float[] rest, int[] influenceBone,
                            float[] influenceWeight, int[] triVert, float[] triUv, boolean[] triGlow) {
        this.boneNames = boneNames;
        this.boneParent = boneParent;
        this.pivot = pivot;
        this.rest = rest;
        this.influenceBone = influenceBone;
        this.influenceWeight = influenceWeight;
        this.triVert = triVert;
        this.triUv = triUv;
        this.triGlow = triGlow;
        this.boneMat = new float[boneNames.length * 12];
        this.skinned = new float[rest.length];
    }

    /** Carga el modelo una sola vez. */
    public static synchronized AbominationMesh get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    public int boneCount() {
        return boneNames.length;
    }

    /** Posicion del hueso con ese nombre, o -1 si el modelo no lo tiene. */
    public int boneIndex(String name) {
        for (int i = 0; i < boneNames.length; i++) {
            if (boneNames[i].equals(name)) {
                return i;
            }
        }
        return -1;
    }

    private static AbominationMesh load() {
        try (InputStream in = AbominationMesh.class.getResourceAsStream(PATH)) {
            if (in == null) {
                throw new IllegalStateException("Falta el recurso " + PATH);
            }
            JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject();

            JsonArray bones = json.getAsJsonArray("bones");
            int boneCount = bones.size();
            String[] names = new String[boneCount];
            int[] parents = new int[boneCount];
            float[] pivots = new float[boneCount * 3];
            for (int i = 0; i < boneCount; i++) {
                JsonObject bone = bones.get(i).getAsJsonObject();
                names[i] = bone.get("name").getAsString();
                parents[i] = bone.get("parent").getAsInt();
                if (parents[i] >= i) {
                    throw new IllegalStateException("El hueso " + names[i] + " va antes que su padre");
                }
                JsonArray p = bone.getAsJsonArray("pivot");
                for (int k = 0; k < 3; k++) {
                    pivots[i * 3 + k] = p.get(k).getAsFloat();
                }
            }

            JsonArray verts = json.getAsJsonArray("verts");
            int vertCount = verts.size() / VERT_STRIDE;
            float[] rest = new float[vertCount * 3];
            int[] infBone = new int[vertCount * INFLUENCES];
            float[] infWeight = new float[vertCount * INFLUENCES];
            for (int v = 0; v < vertCount; v++) {
                int o = v * VERT_STRIDE;
                for (int k = 0; k < 3; k++) {
                    rest[v * 3 + k] = verts.get(o + k).getAsFloat();
                }
                for (int k = 0; k < INFLUENCES; k++) {
                    infBone[v * INFLUENCES + k] = verts.get(o + 3 + k * 2).getAsInt();
                    infWeight[v * INFLUENCES + k] = verts.get(o + 4 + k * 2).getAsFloat();
                }
            }

            JsonArray tris = json.getAsJsonArray("tris");
            int triCount = tris.size() / TRI_STRIDE;
            int[] triVert = new int[triCount * 3];
            float[] triUv = new float[triCount * 6];
            boolean[] triGlow = new boolean[triCount];
            for (int t = 0; t < triCount; t++) {
                int o = t * TRI_STRIDE;
                for (int k = 0; k < 3; k++) {
                    triVert[t * 3 + k] = tris.get(o + k).getAsInt();
                }
                for (int k = 0; k < 6; k++) {
                    triUv[t * 6 + k] = tris.get(o + 3 + k).getAsFloat();
                }
                triGlow[t] = tris.get(o + 9).getAsInt() != 0;
            }
            return new AbominationMesh(names, parents, pivots, rest, infBone, infWeight, triVert, triUv, triGlow);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer " + PATH, e);
        }
    }

    /**
     * Dibuja el modelo con la pose dada.
     *
     * @param rot giros de cada hueso, en el orden del modelo: rot[hueso] = {x, y, z} en radianes
     * @param offsetX desplazamiento de todo el cuerpo, en bloques (agacharse, lanzarse)
     * @param offsetY idem, hacia arriba
     * @param offsetZ idem, -Z es hacia delante
     */
    public void draw(PoseStack pose, VertexConsumer vc, int light, int overlay, int r, int g, int b, int a,
                     float[][] rot, float offsetX, float offsetY, float offsetZ) {
        computeBones(rot);
        skin(offsetX, offsetY, offsetZ);

        Matrix4f m = pose.last().pose();
        Matrix3f nm = pose.last().normal();
        int triCount = triGlow.length;
        for (int t = 0; t < triCount; t++) {
            int i0 = triVert[t * 3] * 3;
            int i1 = triVert[t * 3 + 1] * 3;
            int i2 = triVert[t * 3 + 2] * 3;
            float ax = skinned[i1] - skinned[i0];
            float ay = skinned[i1 + 1] - skinned[i0 + 1];
            float az = skinned[i1 + 2] - skinned[i0 + 2];
            float bx = skinned[i2] - skinned[i0];
            float by = skinned[i2 + 1] - skinned[i0 + 1];
            float bz = skinned[i2 + 2] - skinned[i0 + 2];
            float nx = ay * bz - az * by;
            float ny = az * bx - ax * bz;
            float nz = ax * by - ay * bx;
            float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (len < 1.0E-9F) {
                nx = 0.0F;
                ny = 1.0F;
                nz = 0.0F;
            } else {
                nx /= len;
                ny /= len;
                nz /= len;
            }
            normal.set(nx, ny, nz);
            nm.transform(normal);
            int tl = triGlow[t] ? FULL_BRIGHT : light;
            // El renderizador de entidades dibuja cuadrilateros: el triangulo (v0, v1, v2) va como (v0, v1, v2, v2).
            // Mandar solo 3 vertices mezclaria los de triangulos distintos y deformaria el modelo.
            for (int k = 0; k < 4; k++) {
                int c = Math.min(k, 2);
                int vi = c == 0 ? i0 : (c == 1 ? i1 : i2);
                vc.vertex(m, skinned[vi], skinned[vi + 1], skinned[vi + 2])
                        .color(r, g, b, a)
                        .uv(triUv[t * 6 + c * 2], triUv[t * 6 + c * 2 + 1])
                        .overlayCoords(overlay)
                        .uv2(tl)
                        .normal(normal.x, normal.y, normal.z)
                        .endVertex();
            }
        }
    }

    /**
     * Matriz de cada hueso (3 filas x 4 columnas, por filas): el giro alrededor de su articulacion, dentro del
     * giro de su padre. Orden de los giros: primero X, luego Y y al final Z, como Part.applyTransform de antes.
     */
    private void computeBones(float[][] rot) {
        for (int i = 0; i < boneNames.length; i++) {
            float x = rot[i][0];
            float y = rot[i][1];
            float z = rot[i][2];
            float cx = (float) Math.cos(x);
            float sx = (float) Math.sin(x);
            float cy = (float) Math.cos(y);
            float sy = (float) Math.sin(y);
            float cz = (float) Math.cos(z);
            float sz = (float) Math.sin(z);
            // R = Rz * Ry * Rx
            float r00 = cz * cy;
            float r01 = cz * sy * sx - sz * cx;
            float r02 = cz * sy * cx + sz * sx;
            float r10 = sz * cy;
            float r11 = sz * sy * sx + cz * cx;
            float r12 = sz * sy * cx - cz * sx;
            float r20 = -sy;
            float r21 = cy * sx;
            float r22 = cy * cx;
            float px = pivot[i * 3];
            float py = pivot[i * 3 + 1];
            float pz = pivot[i * 3 + 2];
            // Gira alrededor de la articulacion: t = pivot - R * pivot.
            float tx = px - (r00 * px + r01 * py + r02 * pz);
            float ty = py - (r10 * px + r11 * py + r12 * pz);
            float tz = pz - (r20 * px + r21 * py + r22 * pz);

            int o = i * 12;
            int parent = boneParent[i];
            if (parent < 0) {
                boneMat[o] = r00;
                boneMat[o + 1] = r01;
                boneMat[o + 2] = r02;
                boneMat[o + 3] = tx;
                boneMat[o + 4] = r10;
                boneMat[o + 5] = r11;
                boneMat[o + 6] = r12;
                boneMat[o + 7] = ty;
                boneMat[o + 8] = r20;
                boneMat[o + 9] = r21;
                boneMat[o + 10] = r22;
                boneMat[o + 11] = tz;
            } else {
                int q = parent * 12;
                float p00 = boneMat[q];
                float p01 = boneMat[q + 1];
                float p02 = boneMat[q + 2];
                float p03 = boneMat[q + 3];
                float p10 = boneMat[q + 4];
                float p11 = boneMat[q + 5];
                float p12 = boneMat[q + 6];
                float p13 = boneMat[q + 7];
                float p20 = boneMat[q + 8];
                float p21 = boneMat[q + 9];
                float p22 = boneMat[q + 10];
                float p23 = boneMat[q + 11];
                // M = Parent * A
                boneMat[o] = p00 * r00 + p01 * r10 + p02 * r20;
                boneMat[o + 1] = p00 * r01 + p01 * r11 + p02 * r21;
                boneMat[o + 2] = p00 * r02 + p01 * r12 + p02 * r22;
                boneMat[o + 3] = p00 * tx + p01 * ty + p02 * tz + p03;
                boneMat[o + 4] = p10 * r00 + p11 * r10 + p12 * r20;
                boneMat[o + 5] = p10 * r01 + p11 * r11 + p12 * r21;
                boneMat[o + 6] = p10 * r02 + p11 * r12 + p12 * r22;
                boneMat[o + 7] = p10 * tx + p11 * ty + p12 * tz + p13;
                boneMat[o + 8] = p20 * r00 + p21 * r10 + p22 * r20;
                boneMat[o + 9] = p20 * r01 + p21 * r11 + p22 * r21;
                boneMat[o + 10] = p20 * r02 + p21 * r12 + p22 * r22;
                boneMat[o + 11] = p20 * tx + p21 * ty + p22 * tz + p23;
            }
        }
    }

    /**
     * Mueve cada vertice: la suma de lo que lo moveria cada hueso, por su peso, mas el desplazamiento del cuerpo.
     * Despues, si algo ha quedado bajo el suelo (una pata que se estira al andar, el cuerpo agachado), se sube todo
     * lo justo para que el punto mas bajo toque el suelo: la bestia nunca se hunde en el suelo. Si el cuerpo se
     * levanta (un salto), no se baja.
     */
    private void skin(float offsetX, float offsetY, float offsetZ) {
        int vertCount = rest.length / 3;
        float minY = Float.MAX_VALUE;
        for (int v = 0; v < vertCount; v++) {
            float x = rest[v * 3];
            float y = rest[v * 3 + 1];
            float z = rest[v * 3 + 2];
            float ox = 0.0F;
            float oy = 0.0F;
            float oz = 0.0F;
            for (int k = 0; k < INFLUENCES; k++) {
                float w = influenceWeight[v * INFLUENCES + k];
                if (w <= 0.0F) {
                    continue;
                }
                int o = influenceBone[v * INFLUENCES + k] * 12;
                ox += w * (boneMat[o] * x + boneMat[o + 1] * y + boneMat[o + 2] * z + boneMat[o + 3]);
                oy += w * (boneMat[o + 4] * x + boneMat[o + 5] * y + boneMat[o + 6] * z + boneMat[o + 7]);
                oz += w * (boneMat[o + 8] * x + boneMat[o + 9] * y + boneMat[o + 10] * z + boneMat[o + 11]);
            }
            skinned[v * 3] = ox + offsetX;
            skinned[v * 3 + 1] = oy + offsetY;
            skinned[v * 3 + 2] = oz + offsetZ;
            minY = Math.min(minY, oy + offsetY);
        }
        if (minY < 0.0F) {
            for (int v = 0; v < vertCount; v++) {
                skinned[v * 3 + 1] -= minY;
            }
        }
    }
}
