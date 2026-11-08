#!/usr/bin/env python3
"""
Vista previa de la abominacion, sin abrir Minecraft.

Lee exactamente los mismos archivos que usa el mod (models/abomination_mesh.json y su textura) y hace lo mismo que
AbominationMesh.java: calcula la matriz de cada hueso con los giros de la pose (primero X, luego Y y al final Z,
alrededor de su articulacion y dentro del giro de su padre) y mueve cada vertice con la suma, por sus pesos, de lo que
lo moveria cada hueso ("skinning").

Los giros de cada pose los calcula el codigo REAL del mod (AbominationPose.java) con tools/pose_dump/PoseDump.java,
que escribe un JSON; este script lo dibuja:

  javac -d /tmp/pose_out src/main/java/com/example/pigabomination/entity/AbominationAction.java \\
        src/main/java/com/example/pigabomination/client/AbominationPose.java tools/pose_dump/PoseDump.java
  java -cp /tmp/pose_out PoseDump > /tmp/poses.json
  python3 -I tools/preview_mesh.py hoja.png --angles /tmp/poses.json --scenarios idle,walk_0,bite_16,claw_19
  python3 -I tools/preview_mesh.py reposo.png          # sin giros, de frente, de lado y de espaldas

Opciones:
  --views front,side,back,q   vistas de la hoja (front, side, back, q = tres cuartos; o un angulo en grados).
                              Por defecto: q,side con --angles; front,side,back sin ella
  --bones                     colorea cada triangulo segun su hueso dominante (para revisar los pesos)
  --size 360                  alto en pixeles de cada vista

La imagen se dibuja con puntos (un cuadradito por triangulo, de su color y su sombra): es una vista previa, no el
render del juego, pero muestra la misma geometria, la misma textura y las mismas poses.
"""
import argparse
import json
import math
import os
from multiprocessing import Pool

import numpy as np
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MESH = os.path.join(ROOT, "src/main/resources/assets/pigabomination/models/abomination_mesh.json")
TEX = os.path.join(ROOT, "src/main/resources/assets/pigabomination/textures/entity/abomination_mesh.png")

VIEW_ANGLES = {"front": 0.0, "q": 35.0, "side": 90.0, "back": 180.0}
BONE_COLORS = [
    (0.30, 0.70, 1.00), (1.00, 0.85, 0.20), (1.00, 0.30, 0.30), (0.60, 0.60, 0.60), (0.40, 0.40, 0.40),
    (0.30, 1.00, 0.40), (0.10, 0.60, 0.20), (1.00, 0.40, 0.80), (0.70, 0.20, 0.50),
    (1.00, 0.55, 0.10), (0.70, 0.35, 0.05), (0.70, 0.40, 1.00), (0.40, 0.20, 0.70),
]


def euler(x, y, z):
    """Igual que AbominationMesh.computeBones: R = Rz * Ry * Rx."""
    cx, sx = math.cos(x), math.sin(x)
    cy, sy = math.cos(y), math.sin(y)
    cz, sz = math.cos(z), math.sin(z)
    rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return rz @ ry @ rx


_MODEL = None


def load_model():
    global _MODEL
    if _MODEL is None:
        d = json.load(open(MESH, encoding="utf-8"))
        v = np.array(d["verts"], np.float64).reshape(-1, 9)
        t = np.array(d["tris"], np.float64).reshape(-1, 10)
        tex = np.asarray(Image.open(TEX).convert("RGB"), dtype=np.float32) / 255.0
        _MODEL = {
            "bones": d["bones"],
            "rest": v[:, 0:3],
            "ib": v[:, [3, 5, 7]].astype(int),
            "w": v[:, [4, 6, 8]],
            "tri": t[:, 0:3].astype(int),
            "uv": t[:, 3:9].reshape(-1, 3, 2),
            "glow": t[:, 9] > 0.5,
            "tex": tex,
        }
    return _MODEL


def bone_matrices(m, rot):
    mats = []
    for b in m["bones"]:
        x, y, z = rot.get(b["name"], [0.0, 0.0, 0.0])
        r = euler(x, y, z)
        p = np.array(b["pivot"], np.float64)
        a = np.eye(4)
        a[:3, :3] = r
        a[:3, 3] = p - r @ p
        mats.append(mats[b["parent"]] @ a if b["parent"] >= 0 else a)
    return np.stack(mats)


def skin(m, pose):
    mats = bone_matrices(m, pose.get("rot", {}))
    out = np.zeros_like(m["rest"])
    for k in range(3):
        mk = mats[m["ib"][:, k]]
        pk = np.einsum("vij,vj->vi", mk[:, :3, :3], m["rest"]) + mk[:, :3, 3]
        out += m["w"][:, k:k + 1] * pk
    out = out + np.array(pose.get("offset", [0.0, 0.0, 0.0]))
    # Igual que AbominationMesh.skin: si algo queda bajo el suelo, se sube todo para que el punto mas bajo lo toque.
    low = out[:, 1].min()
    if low < 0.0:
        out[:, 1] -= low
    return out


def render(pose, theta_deg, color_bones, height):
    m = load_model()
    pos = skin(m, pose)
    tri = m["tri"]
    p = pos[tri]  # (T, 3, 3)
    c = p.mean(axis=1)
    n = np.cross(p[:, 1] - p[:, 0], p[:, 2] - p[:, 0])
    ln = np.linalg.norm(n, axis=1)
    n = n / np.maximum(ln, 1e-12)[:, None]
    th = math.radians(theta_deg)
    sx_ = -c[:, 0] * math.cos(th) - c[:, 2] * math.sin(th)
    depth = -c[:, 2] * math.cos(th) + c[:, 0] * math.sin(th)

    H = height
    W = int(H * 1.05)
    scale = H * 0.80 / 2.7
    px = (W / 2 + sx_ * scale).astype(int)
    py = (H * 0.93 - c[:, 1] * scale).astype(int)

    if color_bones:
        dom = m["ib"][np.arange(len(m["ib"])), m["w"].argmax(axis=1)]
        col = np.array(BONE_COLORS, np.float32)[dom[tri[:, 0]] % len(BONE_COLORS)]
    else:
        tex = m["tex"]
        h, w = tex.shape[:2]
        uvc = m["uv"].mean(axis=1)
        tx = np.clip((uvc[:, 0] * w).astype(int), 0, w - 1)
        ty = np.clip((uvc[:, 1] * h).astype(int), 0, h - 1)
        col = tex[ty, tx]
    light = np.array([0.3, 0.8, -0.5])
    light /= np.linalg.norm(light)
    # Sin culling (entityCutoutNoCull): la cara trasera se ve igual.
    shade = np.where(m["glow"], 1.0, 0.55 + 0.45 * np.abs(n @ light)).astype(np.float32)
    col = np.clip(col * shade[:, None], 0.0, 1.0)

    img = np.zeros((H, W, 3), np.float32) + 0.06
    ground = int(H * 0.93)
    img[ground:ground + 1, :, :] = 0.25
    order = np.argsort(depth)  # los lejanos primero: los cercanos se dibujan encima
    px, py, col = px[order], py[order], col[order]
    span = max(2, int(round(H / 130.0)))
    for dy in range(-(span // 2), span - span // 2):
        for dx in range(-(span // 2), span - span // 2):
            xx = px + dx
            yy = py + dy
            ok = (xx >= 0) & (xx < W) & (yy >= 0) & (yy < H)
            img[yy[ok], xx[ok]] = col[ok]
    return Image.fromarray((img * 255).astype(np.uint8))


def job(args):
    label, pose, view, color_bones, height = args
    theta = VIEW_ANGLES[view] if view in VIEW_ANGLES else float(view)
    im = render(pose, theta, color_bones, height)
    ImageDraw.Draw(im).text((6, 4), f"{label} ({view})", fill=(230, 230, 230))
    return im


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("output")
    ap.add_argument("--angles", help="JSON de poses escrito por tools/pose_dump/PoseDump.java")
    ap.add_argument("--scenarios", help="nombres de poses separados por comas (por defecto todas)")
    ap.add_argument("--views", help="front,side,back,q o angulos en grados")
    ap.add_argument("--bones", action="store_true")
    ap.add_argument("--size", type=int, default=360)
    args = ap.parse_args()

    if args.angles:
        poses = json.load(open(args.angles, encoding="utf-8"))
        names = args.scenarios.split(",") if args.scenarios else list(poses)
        views = (args.views or "q,side").split(",")
    else:
        poses = {"reposo": {"rot": {}, "offset": [0, 0, 0]}}
        names = ["reposo"]
        views = (args.views or "front,side,back").split(",")

    jobs = [(name, poses[name], view, args.bones, args.size) for view in views for name in names]
    with Pool(2) as pool:
        images = pool.map(job, jobs)

    cols = len(names)
    cw, ch = images[0].size
    sheet = Image.new("RGB", (cw * cols, ch * len(views)))
    for i, im in enumerate(images):
        sheet.paste(im, ((i % cols) * cw, (i // cols) * ch))
    sheet.save(args.output)
    print("Vista previa:", args.output, f"({len(names)} poses x {len(views)} vistas)")


if __name__ == "__main__":
    main()
