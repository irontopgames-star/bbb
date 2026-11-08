#!/usr/bin/env python3
"""
Convierte el modelo de Blockbench (.bbmodel) de la abominacion al formato del mod:

  src/main/resources/assets/pigabomination/models/abomination_mesh.json
  src/main/resources/assets/pigabomination/textures/entity/abomination_mesh.png

Uso:
  python3 -I tools/bbmodel_to_mesh.py ruta/al/cerdo_2.bbmodel [--height 2.7] [--texture-size 2048]

Que hace:
  1. Suelda los vertices que coinciden y unifica el sentido de las caras, para que las normales miren hacia fuera.
  2. Conserva la malla ORIGINAL, triangulo a triangulo, con sus coordenadas de textura (UV): lo que se ve en
     Blockbench es lo que se dibuja en el juego. No se cambia el diseno ni se pierde detalle.
  3. La malla es UNA sola piel: en vez de trocearla en piezas rigidas (que dejarian huecos en las articulaciones),
     le pone un esqueleto y da a cada vertice la influencia de hasta 3 huesos ("skinning"). Asi los codos, las
     rodillas, los hombros y la mandibula se doblan sin abrir grietas. Los huesos estan en BONES, abajo.
  4. Marca como "brillantes" los triangulos de los ojos (el blanco de la textura): se dibujan a plena luz.
  5. Escribe la textura (reducida a --texture-size) y el JSON.

Convenciones del resultado (en bloques):
  - los pies estan en y = 0 y el eje Y apunta hacia arriba;
  - la cara mira hacia -Z (como los modelos de vanilla): el modelo se gira 180 grados respecto al .bbmodel;
  - la altura total es --height (por defecto 2,7 bloques, la altura de un golem de hierro).
  - "izquierda" (_l) es el lado +X del resultado, que en el .bbmodel es -X.

Formato del JSON:
  "bones":  [{"name", "parent" (-1 = ninguno), "pivot": [x, y, z]}, ...]   padres siempre antes que hijos
  "verts":  9 valores por vertice: x, y, z, y tres pares (hueso, peso)
  "tris":   10 valores por triangulo: v0, v1, v2 (indices de vertice), u0, v0, u1, v1, u2, v2 y 1 si brilla
En el juego cada triangulo se dibuja como un cuadrilatero degenerado (v0, v1, v2, v2): el renderizador de entidades
de Minecraft trabaja con cuadrilateros, y mandar triangulos de 3 vertices mezcla los vertices y deforma el modelo.
"""
import argparse
import base64
import io
import json
import math
import os
from collections import defaultdict, deque

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_JSON = os.path.join(ROOT, "src/main/resources/assets/pigabomination/models/abomination_mesh.json")
OUT_TEX = os.path.join(ROOT, "src/main/resources/assets/pigabomination/textures/entity/abomination_mesh.png")

# ---- Esqueleto ----------------------------------------------------------------------------------------------------
# Coordenadas del .bbmodel (unidades de Blockbench): la cara mira a +Z, Y hacia arriba, el centro en x = z = 0.
# hueso: (padre, articulacion, [capsulas (a, b, radio)])
# La influencia de un hueso sobre un vertice depende de lo cerca que esta de sus capsulas: un vertice pegado a
# la piel de un brazo es del brazo; uno en la union entre brazo y pecho se reparte entre los dos.
# El orden importa: los padres van antes que los hijos. AbominationPose usa estos mismos nombres y este orden.
BONES = {
    # El cuerpo: gira desde la pelvis. Lleva todo menos las patas traseras (se quedan plantadas).
    "body": (None, (0.0, 2.0, -5.5), [((0, 2, -5.5), (0, 8, 0.5), 6.0), ((0, 9, -1.5), (0, 9, 1.5), 4.5)]),
    "head": ("body", (0.0, 8.0, 3.0), [((0, 8.0, 3.0), (0, 8.8, 10.5), 3.8), ((-5.6, 11.2, 3.6), (5.6, 11.2, 3.6), 2.0)]),
    "jaw": ("head", (0.0, 6.4, 4.6), [((0, 6.4, 4.6), (0, 5.0, 10.5), 2.6)]),
    "tail1": ("body", (0.0, 2.5, -9.5), [((0, 2.5, -9.5), (-2.6, -6.0, -10.6), 1.1)]),
    "tail2": ("tail1", (-2.6, -6.0, -10.6), [((-2.6, -6.0, -10.6), (-1.2, -10.2, -11.2), 1.1)]),
    # Patas delanteras (los "brazos"): hombro y codo.
    "arm_l": ("body", (-5.4, 8.0, 2.4), [((-5.4, 8.0, 2.4), (-8.5, -4.5, 4.0), 3.2)]),
    "fore_l": ("arm_l", (-8.5, -4.5, 4.0), [((-8.5, -4.5, 4.0), (-7.2, -14.5, 6.0), 3.0)]),
    "arm_r": ("body", (5.2, 8.0, 2.3), [((5.2, 8.0, 2.3), (8.5, -4.5, 4.4), 3.2)]),
    "fore_r": ("arm_r", (8.5, -4.5, 4.4), [((8.5, -4.5, 4.4), (7.3, -14.5, 7.8), 3.0)]),
    # Patas traseras: cadera y corvejon (el "tobillo" que se dobla hacia atras).
    "thigh_l": (None, (-3.6, 2.0, -4.0), [((-3.6, 2.0, -4.0), (-4.8, -7.5, -9.5), 3.0)]),
    "shin_l": ("thigh_l", (-4.8, -7.5, -9.5), [((-4.8, -7.5, -9.5), (-6.0, -14.5, -6.2), 2.4)]),
    "thigh_r": (None, (3.6, 2.0, -4.0), [((3.6, 2.0, -4.0), (4.2, -8.0, -8.8), 3.0)]),
    "shin_r": ("thigh_r", (4.2, -8.0, -8.8), [((4.2, -8.0, -8.8), (4.9, -14.5, -5.5), 2.4)]),
}
NAMES = list(BONES)
SIGMA = 1.0       # unidades de Blockbench: cuanto se mezclan dos huesos en su frontera
MAX_INFLUENCES = 3
MIN_WEIGHT = 0.02

# Los ojos: los triangulos casi blancos de la cara. Hay otros blancos en la textura (los dientes), asi que solo se
# buscan dentro de esta caja (unidades de Blockbench: y, z) y se amplian a los triangulos vecinos.
EYE_Y = (9.8, 11.2)
EYE_Z = (7.5, 9.5)
EYE_SEED_MIN = 0.5     # el minimo de los tres canales de la textura, de 0 a 1
EYE_NEIGHBOR_MIN = 0.35
EYE_RADIUS = 0.55


def sub(a, b):
    return (a[0] - b[0], a[1] - b[1], a[2] - b[2])


def cross(a, b):
    return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])


def dot(a, b):
    return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]


def weld_and_orient(verts, faces):
    """Devuelve (posiciones soldadas, triangulos [{'w': [3 ids], 'uv': [3 (u,v)]}]) con orientacion unificada."""
    ids = {}
    pos = []

    def wid(p):
        key = (round(p[0], 5), round(p[1], 5), round(p[2], 5))
        if key not in ids:
            ids[key] = len(pos)
            pos.append((p[0], p[1], p[2]))
        return ids[key]

    tris = []
    for face in faces.values():
        vids = face["vertices"]
        uvs = face["uv"]
        for i in range(1, len(vids) - 1):
            corners = (vids[0], vids[i], vids[i + 1])
            w = [wid(verts[c]) for c in corners]
            uv = [tuple(uvs[c]) for c in corners]
            if len(set(w)) < 3:
                continue  # triangulo degenerado
            tris.append({"w": w, "uv": uv})

    # Triangulos que comparten una arista: cada pareja debe recorrerla en sentido contrario.
    edge_map = defaultdict(list)
    for ti, t in enumerate(tris):
        w = t["w"]
        for a, b in ((w[0], w[1]), (w[1], w[2]), (w[2], w[0])):
            edge_map[(min(a, b), max(a, b))].append(ti)

    def flip(ti):
        t = tris[ti]
        t["w"][1], t["w"][2] = t["w"][2], t["w"][1]
        t["uv"][1], t["uv"][2] = t["uv"][2], t["uv"][1]

    visited = [False] * len(tris)
    flipped_by_walk = 0
    components = []
    for start in range(len(tris)):
        if visited[start]:
            continue
        visited[start] = True
        comp = [start]
        queue = deque([start])
        while queue:
            ti = queue.popleft()
            w = tris[ti]["w"]
            for a, b in ((w[0], w[1]), (w[1], w[2]), (w[2], w[0])):
                for tj in edge_map[(min(a, b), max(a, b))]:
                    if visited[tj]:
                        continue
                    wj = tris[tj]["w"]
                    same_dir = (a, b) in ((wj[0], wj[1]), (wj[1], wj[2]), (wj[2], wj[0]))
                    if same_dir:
                        flip(tj)
                        flipped_by_walk += 1
                    visited[tj] = True
                    comp.append(tj)
                    queue.append(tj)
        components.append(comp)

    # Cada trozo conectado con volumen negativo esta del reves: se invierte entero.
    flipped_components = 0
    for comp in components:
        volume = 0.0
        for ti in comp:
            p0, p1, p2 = (pos[k] for k in tris[ti]["w"])
            volume += dot(p0, cross(p1, p2))
        if volume < 0:
            flipped_components += 1
            for ti in comp:
                flip(ti)

    print(f"Triangulos: {len(tris)}, trozos conectados: {len(components)}")
    print(f"Invertidos al unificar: {flipped_by_walk} (vecinos) y {flipped_components} trozos enteros")
    return pos, tris


def seg_dist(p, a, b):
    a = np.array(a, float)
    b = np.array(b, float)
    ab = b - a
    t = np.clip(((p - a) @ ab) / (ab @ ab), 0.0, 1.0)
    return np.linalg.norm(p - (a + t[:, None] * ab), axis=1)


def skin_weights(pos):
    """Pesos de cada vertice (n x huesos): suma 1, como mucho MAX_INFLUENCES no nulos."""
    e = np.stack([np.min([seg_dist(pos, a, b) - r for a, b, r in BONES[n][2]], axis=0) for n in NAMES], axis=1)
    w = np.exp(-(e - e.min(axis=1, keepdims=True)) / SIGMA)
    keep = np.argsort(-w, axis=1)[:, :MAX_INFLUENCES]
    out = np.zeros_like(w)
    rows = np.arange(len(w))[:, None]
    out[rows, keep] = w[rows, keep]
    out[out < MIN_WEIGHT * out.sum(axis=1, keepdims=True)] = 0.0
    return out / out.sum(axis=1, keepdims=True)


def texture_lows(tex_px, tw, th, uv):
    """Minimo de los canales en el centro del triangulo y en tres puntos hacia las esquinas (de 0 a 1)."""
    cu = sum(p[0] for p in uv) / 3.0
    cv = sum(p[1] for p in uv) / 3.0
    samples = [(cu, cv)] + [(0.5 * (p[0] + cu), 0.5 * (p[1] + cv)) for p in uv]
    out = []
    for u, v in samples:
        tx = min(tw - 1, max(0, int(u / 16.0 * tw)))
        ty = min(th - 1, max(0, int(v / 16.0 * th)))
        out.append(min(tex_px[tx, ty][:3]) / 255.0)
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("bbmodel")
    ap.add_argument("--height", type=float, default=2.7)
    ap.add_argument("--texture-size", type=int, default=2048)
    args = ap.parse_args()

    with open(args.bbmodel, encoding="utf-8") as fh:
        model = json.load(fh)

    elements = model.get("elements", [])
    if len(elements) != 1 or elements[0].get("type") != "mesh":
        raise SystemExit("Se esperaba un unico elemento de tipo malla (mesh) en el .bbmodel")
    el = elements[0]
    verts = el["vertices"]
    faces = el["faces"]
    uv_w = float(model.get("resolution", {}).get("width", 16))
    uv_h = float(model.get("resolution", {}).get("height", 16))
    if uv_w != 16.0 or uv_h != 16.0:
        raise SystemExit("Se esperaba una resolucion de UV de 16x16 en el .bbmodel")

    allp = np.array(list(verts.values()), float)
    lo, hi = allp.min(axis=0), allp.max(axis=0)
    # El centro del .bbmodel que usan los huesos es (0, _, 0): se mide la desviacion por si otro modelo no esta centrado.
    cx = (lo[0] + hi[0]) / 2.0
    cz = (lo[2] + hi[2]) / 2.0
    ymin = lo[1]
    s = args.height / (hi[1] - lo[1])  # bloques por unidad de Blockbench
    print(f"Caja del modelo (unidades): x {lo[0]:.1f}..{hi[0]:.1f}, y {lo[1]:.1f}..{hi[1]:.1f}, z {lo[2]:.1f}..{hi[2]:.1f}")
    if abs(cx) > 1.0 or abs(cz) > 1.0:
        print(f"AVISO: el modelo no esta centrado (x {cx:.2f}, z {cz:.2f}); los huesos de BONES esperan el centro en 0.")

    tex_src = model["textures"][0]["source"]
    tex_full = Image.open(io.BytesIO(base64.b64decode(tex_src.split(",", 1)[1]))).convert("RGBA")
    tex_px = tex_full.load()
    tw, th = tex_full.size

    pos, tris = weld_and_orient(verts, faces)
    pos_np = np.array(pos, float)
    weights = skin_weights(pos_np)

    # A bloques, ya girado 180 grados sobre Y: la cara pasa de +Z a -Z.
    def to_mod(p):
        return (-(p[0] - cx) * s, (p[1] - ymin) * s, -(p[2] - cz) * s)

    # Los ojos.
    cents = []
    lows = []
    for t in tris:
        p = [pos[k] for k in t["w"]]
        cents.append((sum(a[0] for a in p) / 3.0, sum(a[1] for a in p) / 3.0, sum(a[2] for a in p) / 3.0))
        lows.append(texture_lows(tex_px, tw, th, t["uv"]))
    seeds = [c for c, lw in zip(cents, lows)
             if lw[0] >= EYE_SEED_MIN and EYE_Y[0] <= c[1] <= EYE_Y[1] and EYE_Z[0] <= c[2] <= EYE_Z[1]]
    glow = []
    for c, lw in zip(cents, lows):
        near = any(math.dist(c, sd) < EYE_RADIUS for sd in seeds)
        glow.append(1 if near and max(lw) >= EYE_NEIGHBOR_MIN else 0)

    # Vertices: solo los que usa algun triangulo.
    used = sorted({k for t in tris for k in t["w"]})
    remap = {old: new for new, old in enumerate(used)}
    vert_out = []
    for old in used:
        x, y, z = to_mod(pos[old])
        w = weights[old]
        order = [i for i in np.argsort(-w)[:MAX_INFLUENCES] if w[i] > 0.0]
        vert_out.extend([round(x, 4), round(y, 4), round(z, 4)])
        for k in range(MAX_INFLUENCES):
            if k < len(order):
                vert_out.extend([int(order[k]), round(float(w[order[k]]), 3)])
            else:
                vert_out.extend([0, 0])

    tri_out = []
    degenerate = 0
    for t, g in zip(tris, glow):
        ids3 = [remap[k] for k in t["w"]]
        q = [to_mod(pos[k]) for k in t["w"]]
        n = cross(sub(q[1], q[0]), sub(q[2], q[0]))
        if dot(n, n) < 1e-14:
            degenerate += 1
            continue
        tri_out.extend(ids3)
        for u, v in t["uv"]:
            tri_out.extend([round(u / 16.0, 5), round(v / 16.0, 5)])
        tri_out.append(g)

    bones_out = []
    for name in NAMES:
        parent, pivot, _ = BONES[name]
        x, y, z = to_mod(pivot)
        bones_out.append({
            "name": name,
            "parent": NAMES.index(parent) if parent else -1,
            "pivot": [round(x, 5), round(y, 5), round(z, 5)],
        })

    out = {"height": args.height, "bones": bones_out, "verts": vert_out, "tris": tri_out}
    os.makedirs(os.path.dirname(OUT_JSON), exist_ok=True)
    with open(OUT_JSON, "w", encoding="utf-8") as fh:
        json.dump(out, fh, separators=(",", ":"))

    os.makedirs(os.path.dirname(OUT_TEX), exist_ok=True)
    size = args.texture_size
    tex_out = tex_full if size == tw else tex_full.resize((size, size), Image.LANCZOS)
    tex_out.save(OUT_TEX, optimize=True)

    n_tris = len(tri_out) // 10
    print(f"Vertices: {len(vert_out) // 9}, triangulos: {n_tris} ({sum(glow)} brillantes, {degenerate} degenerados descartados)")
    print(f"Ojos: {len(seeds)} triangulos semilla")
    dom = weights.argmax(axis=1)
    print("Vertices por hueso dominante:", {n: int((dom == i).sum()) for i, n in enumerate(NAMES)})
    print("Altura:", args.height, "bloques, escala:", round(s, 6), "bloques/unidad")
    print("Modelo:", OUT_JSON, f"({os.path.getsize(OUT_JSON) // 1024} KB)")
    print("Textura:", OUT_TEX, f"{size}x{size} ({os.path.getsize(OUT_TEX) // 1024} KB)")


if __name__ == "__main__":
    main()
