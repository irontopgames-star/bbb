# The Pig Abomination (Forge 1.20.1) — versión 1.1.0

Proyecto del mod de terror. La historia dura cinco días y el mundo guarda el progreso.

## Qué hace ahora

**De día (días 1 a 4):** de vez en cuando aparece un cerdo normal lejos. Se acerca hasta unos 14 bloques y te mira.
Suena al aparecer y al irse. Si te acercas a menos de 6 bloques, se aleja y desaparece con humo.

**De noche (noches 1 a 4):** aparece la abominación ya transformada. Cada noche es más intensa: aparece más seguido,
los golpes en puertas y ventanas son más frecuentes, y el máximo de apariciones sube de 2 a 3.
Mientras una aparición está cerca, tu visión se oscurece.

- **Noche 1:** hasta dos apariciones repentinas a tu lado. Si la miras, desaparece; si no, se va a los 3 segundos.
  Aparecen también un cadáver de animal en el jardín y una cabeza junto a la puerta. Se quedan hasta el anochecer;
  sus moscas aparecen cuando sale el sol.
- **Noche 2:** dos apariciones separadas por 2,5 minutos. Primero, frente a una ventana de cristal o reja,
  mirándote a través del cristal; después, una repentina a tu lado. Si estás dentro de la casa, cada golpe en una puerta o ventana acaba,
  unos segundos después, en rotura (60% de las veces, como máximo 3 por noche), con una risa.
- **Noche 3:** hasta tres apariciones: frente a una ventana si hay; si no, te acecha a 10–14 bloques: se acerca si
  estás lejos y se detiene al verte. Mientras te acecha, caza vacas, ovejas, pollos y cerdos normales cercanos:
  los golpea y los mata sin botín, y deja su cadáver con moscas, que desaparece a los 2 minutos.
- **Noche 4:** hasta tres apariciones: frente a una puerta abierta, luego una ventana, y después una repentina a tu
  lado, que se lanza hacia ti. Si estás a menos de 4 bloques de una aparición, te hace 4 de daño (2 corazones) y
  desaparece. Las de puerta y ventana te miran desde fuera, así que el daño llega si te acercas a la abertura.
  Si estás dentro, los golpes rompen puertas y ventanas, con risa. A veces aparecen una cabeza junto a la puerta o un cadáver cerca
  de la casa.

Entre dos apariciones de la misma noche hay un tiempo de espera.

**Día 5 al anochecer (la pelea):**

1. **Transformación (unos 5 segundos).** Un cerdo (el más cercano, o uno que aparece delante de ti) se queda quieto
   mirándote. Silencio durante un segundo. Después el cerdo dice **«voy a matarte»** y empieza a temblar; sale humo
   y ceniza; un gruñido muy grave, el cuerpo se hincha hasta un 35 % más grande, con esporas rojas; un chillido
   deformado, el mundo se oscurece y se oye un rugido. Mientras cambia es invulnerable.
2. **Surge (4 segundos).** El cerdo desaparece y la abominación se levanta encogida, tiembla, se incorpora y ruge.
   Sigue invulnerable y la barra de vida aún no se ve. Al final del rugido aparece la barra **THE PIG ABOMINATION**
   (300 de vida) y empieza a atacar.
3. **La pelea.** Es una bestia de **cuatro patas**: camina con pasos cruzados y galopa cuando estás lejos. Tiene tres ataques, todos
   con aviso, para poder esquivarlos (deja de girarse hacia ti unos 0,25 s antes del golpe):

   | Ataque | Aviso | Daño | Cuándo |
   |---|---|---|---|
   | **Mordisco** | echa la cabeza atrás con la boca abierta y la lanza hacia delante | 8 | a menos de 3,4 bloques |
   | **Arañazo** | levanta un brazo y barre de lado; empuja y levanta un poco | 6 | a menos de 4,2 bloques |
   | **Embestida** | se agacha a cuatro patas, ruge y escarba el suelo; después corre en línea recta | 12 | entre 7 y 22 bloques, cada 9–14 s |

   Si la embestida acierta, se recupera. Si choca con un muro, lo rompe (con `mobGriefing`) y queda **aturdida**
   unos 2,5 s: recibe un 50 % más de daño. A mitad de vida **se enfurece**: ruge, ataca más seguido y corre más.
   Si un bloque le cierra el paso, lo rompe. Cada vez que la golpeas, **grita** (como mucho un grito cada 0,75 s,
   para que no se amontonen).
4. **Final.** Al morir cae y se queda tendida unos 3,5 s antes de desvanecerse. La barra desaparece, suelta
   500 de experiencia, se avisa a todos los jugadores y la victoria queda guardada en el mundo (`/pigabomination
   status` muestra "jefe derrotado"); a partir de ahí no vuelve a aparecer nada.

**Sonidos:** de noche, cuando todo está en calma y no hay ninguna criatura cerca, una puerta o una ventana cercana
suena con dos golpes o tres toques, para dar suspenso. Solo se oyen si estás dentro de la casa, y nunca
durante una aparición. También hay un gruñido de
vez en cuando mientras la abominación acecha o te mira; un zumbido con golpe metálico al aparecer; un rugido al
transformarse y un gruñido corto durante la pelea. El cerdo de día suena al aparecer y al irse. Son grabaciones del
autor, convertidas a OGG mono y normalizadas, en `src/main/resources/assets/pigabomination/sounds/` con los nombres
de `sounds.json`: para cambiar un sonido, reemplaza su archivo con el mismo nombre.
La risa (`laugh.ogg`) viene de la grabación `risa.mp3` del autor, y suena al romperse una puerta o una ventana.
Tres voces más del autor:

- `taunt.ogg` (de `tienes_miedo.mp3`): **«tienes miedo»**, solo en las apariciones de las noches 2, 3 y 4. Suena donde
  apareció la criatura, 2 segundos después, cuando el zumbido de la aparición ya se apaga (si no, lo taparía). Suena
  aunque la criatura ya se haya ido. En la noche 1 no suena.
- `threat.ogg` (de `voy_a_matarte.mp3`): **«voy a matarte»**, la voz del cerdo en la transformación de la noche 5.
- `scream.ogg` (de `grito.mp3`): el **grito** al golpear a la abominación.

## Modelo de la abominación (cerdo_2.bbmodel, versión 1.1.0)

La abominación usa la malla ORIGINAL de `cerdo_2.bbmodel` (la bestia de cuatro patas), triángulo a triángulo y con su
textura (4096×4096, reducida a 2048×2048 en el juego): lo que ves en Blockbench es lo que se dibuja en el juego (cara, hocico,
dientes, garras, espinas del lomo). Son 17 777 triángulos y 9 132 vértices.

Mide 2,7 bloques de alto en reposo (como un golem de hierro) y su hitbox es de 1,6 × 2,7. Los ojos (los triángulos de
textura clara de la cara) se dibujan a plena luz, así que brillan en la oscuridad.

**Una sola piel con esqueleto.** La malla es una única superficie: trocearla en piezas rígidas dejaría huecos en codos,
rodillas y hombros. En su lugar, `tools/bbmodel_to_mesh.py` le pone un esqueleto de 13 huesos y da a cada vértice la
influencia de hasta 3 huesos según lo cerca que esté de ellos ("skinning"); en el juego, `AbominationMesh` calcula
las matrices de los huesos con los giros de `AbominationPose`, mueve cada vértice una vez por fotograma y dibuja.
Los huesos son: `body` (el tronco, gira desde la pelvis), `head`, `jaw` (mandíbula), `tail1`, `tail2`, las patas
delanteras `arm_l/fore_l` y `arm_r/fore_r` (hombro y codo), y las traseras `thigh_l/shin_l` y `thigh_r/shin_r`
(cadera y corvejón).

**Animación (cuatro patas).** Todos los giros están en `client/AbominationPose.java`, que es solo matemática:
el reposo es el propio modelo; el servidor decide la acción y la sincroniza (`AbominationAction`) y el cliente la anima.
Los tiempos de cada acción y del instante del golpe siguen en `entity/AbominationAction.java`, así que animación y daño coinciden.

- **Quieta:** respira, la cola se mece, la mandíbula cuelga y la cabeza se balancea.
- **Andar y correr:** pasos cruzados (mano izquierda con pata derecha); cada pata se levanta doblando el codo o el corvejón.
  Al correr, zancada más larga, cuerpo más estirado y cola hacia atrás.
- **Mordisco:** echa la cabeza atrás con las fauces abiertas y la lanza hacia delante, cerrándolas de golpe.
- **Arañazo:** se alza un poco, levanta la garra derecha y barre de lado; la otra mano se afirma en el suelo.
- **Embestida:** se agacha y escarba el suelo con las garras; después galopa con las manos juntas y las patas traseras juntas.
- **Aturdida, recuperación, rugido (se alza sobre las patas traseras con los brazos abiertos) y surgir.**
- **Mirar:** la cabeza sigue al jugador, con límite de giro. Al recibir un golpe se echa un momento hacia atrás.

El modelo no se hunde en el suelo: si una pata se estira o el cuerpo se agacha y algo queda bajo el suelo, se sube todo
lo justo para que el punto más bajo lo toque (`AbominationMesh.skin`).

Para regenerar la malla desde otro `.bbmodel` (tiene que ser un único elemento de tipo malla, con UV de 16×16 y su textura incrustada):

    python3 -I tools/bbmodel_to_mesh.py ruta/al/cerdo_2.bbmodel --height 2.7

El script suelda los vértices, orienta las normales hacia fuera, calcula los pesos del esqueleto y escribe
`models/abomination_mesh.json` y `textures/entity/abomination_mesh.png`. Los huesos (articulaciones y cápsulas
de influencia) están al principio de `tools/bbmodel_to_mesh.py`, en unidades de Blockbench; si usas otra malla, hay que
ajustarlos a su cuerpo. `AbominationPose` usa los mismos nombres y el mismo orden.

Para ver las poses sin abrir Minecraft. Los giros los calcula el código REAL del mod (`AbominationPose`) y el
script los dibuja sobre los mismos archivos de malla y textura que usa el juego, con el mismo cálculo de huesos:

    javac -d /tmp/pose_out src/main/java/com/example/pigabomination/entity/AbominationAction.java \
          src/main/java/com/example/pigabomination/client/AbominationPose.java tools/pose_dump/PoseDump.java
    java -cp /tmp/pose_out PoseDump > /tmp/poses.json
    python3 -I tools/preview_mesh.py hoja.png --angles /tmp/poses.json --scenarios idle,walk_0,run_0,bite_16,claw_19,windup_14,charge_4 --views side
    python3 -I tools/preview_mesh.py reposo.png                  # sin giros: de frente, de lado y de espaldas
    python3 -I tools/preview_mesh.py pesos.png --bones --views q,side   # colorea cada triángulo por su hueso

Las poses disponibles son `idle`, `walk_0..3`, `run_0..3`, `bite_N`, `claw_N`, `windup_N`, `charge_N`, `stagger_N`,
`recover_10`, `roar_N`, `emerge_N` y `hurt` (N = tick de la acción). La vista previa dibuja un punto por triángulo:
no es el render del juego, pero la geometría y los giros son los mismos (comprobado: el código Java de la malla y la vista
previa dan los mismos vértices con una diferencia de menos de 0,0001).

## Configuración

El mod crea al arrancar el archivo **`config/pigabomination-common.toml`** (en el servidor, el de su carpeta
`config/`). Cada opción lleva su explicación y su mínimo y máximo; un valor fuera de rango vuelve al valor por
defecto en vez de romper nada. La **vida y la velocidad** de las criaturas se aplican al reiniciar el juego o el
servidor; el resto se lee cada vez que se usa, y Forge recarga el archivo si lo editas con el juego abierto.
Las listas de cuatro números son para las noches 1, 2, 3 y 4.

| Sección | Opciones (valor por defecto) |
|---|---|
| `[boss]` | `health` (300), `movementSpeed` (0.28), `biteDamage` (8), `clawDamage` (6), `chargeDamage` (12), `chargeSpeed` (0.55), `enragedChargeSpeed` (0.62), `chargeCooldownSeconds` (9), `enragedChargeCooldownSeconds` (5.5), `enrageAtHealth` (0.5; 0 = nunca), `xpReward` (500) |
| `[pig]` | `health` (300), `movementSpeed` (0.2) |
| `[appearances]` | `enableDayPig` (true), `dayPigChance` (0.002 por segundo), `dayPigCooldownSeconds` (600), `enableNightAppearances` (true), `nightChance` ([0.008, 0.012, 0.016, 0.022]), `maxPerNight` ([2, 2, 3, 3]), `gapSeconds` ([300, 150, 200, 150]), `encounterSeconds` (45), `flashSeconds` (3), `nightFourHitDamage` (4; 0 = sin daño) |
| `[knocks]` | `enableKnocks` (true), `minSeconds` ([30, 22, 16, 10]), `randomSeconds` ([16, 14, 10, 8]), `breakChance` (0.6), `maxBreaksPerNight` (3) |
| `[remains]` | `enableRemains` (true), `chance` ([0.006, 0, 0, 0.012]), `maxNear` (3), `corpseSeconds` (120), `headSeconds` (120) |
| `[world]` | `enableFinalHunt` (true), `huntAnimals` (true), `allowBlockBreaking` (true) |
| `[audio]` | `tauntVolume` (1.5), `threatVolume` (2.0), `screamVolume` (1.6) |

Ejemplos: para una pelea más fácil, `[boss] health = 150` y `biteDamage = 4`. Para un servidor tranquilo,
`[world] allowBlockBreaking = false` y `huntAnimals = false` (además, el mod respeta siempre la regla `mobGriefing`).
Para quitar el final y dejar solo el terror de las noches, `[world] enableFinalHunt = false`.

Lo que **no** está en la configuración, a propósito: la duración de la transformación (5 s) y los tiempos de cada
ataque (`AbominationAction`), porque están sincronizados con la animación y con las voces; si se cambiaran, el aviso
de cada ataque dejaría de coincidir con el golpe.

## Robustez (Fase 8)

- El director de apariciones (`HorrorDirector`) guarda unos contadores en memoria (esperas, golpes pendientes).
  En un mundo de un jugador esos datos sobrevivían al salir al menú y retenían al jugador y al nivel anteriores;
  ahora se borran al cerrar el servidor (`ServerStoppedEvent`).
- La barra de vida de la pelea solo la ven los jugadores de esa dimensión a menos de 64 bloques; se quita a los
  que se alejan (con margen de 16 bloques para que no parpadee), a los desconectados y a los que cambian de dimensión.
- El código común (entidades, director, configuración) no usa ninguna clase del cliente, así que carga en un servidor
  dedicado. Los renderizadores y modelos están en `client/` y solo se registran con `Dist.CLIENT`.
- Trabajo por tick: el acecho consulta al jugador más cercano una vez por tick; las búsquedas pesadas (puertas y
  ventanas cercanas, comprobar si estás dentro de la casa, buscar suelo) solo ocurren al sacar una aparición o
  cada 10 a 30 s en los golpes de calma, y la búsqueda de presas de la noche 3, cada 2 s; ninguna ocurre en cada tick. La comprobación de "dentro de la casa" revisa como
  máximo 1500 casillas. El director corre una vez por segundo y solo en el Overworld.

## Qué NO incluye todavía

- La transformación y la pelea están hechas con el código de Minecraft que no he podido ejecutar: los tiempos de
  los ataques (sobre todo el margen para esquivar) hay que probarlos jugando y ajustarlos.
- Las animaciones de cuatro patas las he revisado en la vista previa, no en el juego: la amplitud de la marcha, la altura a la que
  se alza al rugir y cuánto se agacha en la embestida son cosas para ajustar mirándola en movimiento (`AbominationPose.java`).
- Al rugir o surgir se alza sobre las patas traseras y la cabeza sube a unos 3,1 bloques, por encima de la hitbox (2,7).
- El director solo actúa en el Overworld: en el Nether o el End no hay apariciones ni golpes.
- El número de apariciones de "esta noche" vive en memoria: si cierras el mundo y lo vuelves a abrir en medio de una
  noche, la cuenta de esa noche empieza de cero.

## Compilar

1. IntelliJ IDEA: *Open* → selecciona `build.gradle` → *Open as Project*. Espera a que Gradle sincronice.
   Eclipse: `gradlew eclipse` e importa el proyecto.
2. Ejecutar `gradlew genIntellijRuns` (IDEA) y lanzar `runClient` para probar.
3. Empaquetar: `gradlew build` (o `gradle build`) → el jar queda en `build/libs/pigabomination-1.1.0.jar`.

En GitHub, la acción `Build mod` compila el proyecto y sube el jar como artefacto (`pig-abomination-jar`).

## Probar (mundo de prueba con trucos activados)

- `/pigabomination encounter` → abominación de acecho delante de ti, según la noche en la que estés
  (la voz «tienes miedo» solo suena si estás en las noches 2 a 4: `/time set 37000` es el anochecer de la noche 2).
- `/pigabomination pig` → cerdo de día delante de ti, mirándote.
- `/pigabomination hunt` → transformación (5 s) y pelea ahora mismo, con la barra de vida.
- `/pigabomination status` → día, hora y fase. `/pigabomination reset` → reinicia la historia.
- Sin comandos: deja pasar el tiempo. De día aparece el cerdo de vez en cuando; de noche, la abominación.
  Para saltar a una noche: `/time set 37000` (noche 2), `61000` (noche 3), `85000` (noche 4) y `109000` (noche 5: empieza la
  transformación). `/time set` pone la hora absoluta del mundo, no la hora del día.

Funciona en cualquier dificultad, incluida la pacífica.

## Estructura

```
src/main/java/com/example/pigabomination/
  PigAbominationMod.java         punto de entrada
  config/PigAbominationConfig    configuracion (config/pigabomination-common.toml)
  registry/                      ModEntities, ModSounds, ModItems, ModCreativeTabs, ModEvents
  entity/PigAbominationEntity    el cerdo de día: se acerca, te mira y se va; y la transformación del día 5
  entity/AbominationEntity       la abominación: acecho, caza de animales, daño de la noche 4, pelea del día 5
  entity/AbominationAction       acciones de la pelea (mordisco, arañazo, embestida...) y sus tiempos
  entity/AnimalRemainsEntity     restos de animales: cadáver y cabeza (con su tiempo de vida)
  entity/PreyKind                vaca, oveja, pollo y cerdo normal, las presas de la caza
  client/                        ClientSetup, renderizadores (cerdo, abominación, restos),
                                 AbominationMesh (malla con esqueleto), AbominationModel y AbominationPose (animación a cuatro patas)
  progression/                   HorrorWorldData (guardado), HorrorNightManager (reloj),
                                 HorrorDirector (qué aparece, dónde y cuándo; golpes y roturas), HorrorEvents, HorrorCommands
src/main/resources/
  META-INF/mods.toml, pack.mcmeta
  assets/pigabomination/lang/    en_us.json, es_es.json
  assets/pigabomination/sounds.json y sounds/   sonidos del mod (OGG)
  assets/pigabomination/models/            abomination_mesh.json (malla de la abominación)
  assets/pigabomination/textures/entity/   pig_abomination.png, abomination_mesh.png, fly.png
tools/bbmodel_to_mesh.py                   convierte el .bbmodel a la malla del mod
tools/preview_mesh.py                      vista previa del modelo sin abrir Minecraft
tools/pose_dump/PoseDump.java              escribe los giros reales de cada pose para la vista previa
```

El paquete `com.example.pigabomination` viene del documento del proyecto; si prefieres otro, cambia
`mod_group_id` en `gradle.properties` y mueve las carpetas de `src/main/java`.

## Estado honesto

No he podido compilar este proyecto en el entorno donde se generó (los repositorios de Forge no están
accesibles desde allí). Solo he comprobado la sintaxis: los errores que quedan son de clases de Minecraft
y Forge que no están disponibles aquí. Algunas llamadas de esta versión no las puedo verificar sin el
código de Minecraft: la barra de vida (`ServerBossEvent`), `Entity.remove(RemovalReason)`, la muerte lenta
(`tickDeath`), el temblor y el crecimiento del cerdo (`setupRotations` y `scale` del renderizador), la malla propia de la
abominación (`EntityModel`, `prepareMobModel`, `renderToBuffer`, y el giro de vanilla que el modelo deshace; la
geometría sí la probé cargando la malla con el mismo código del mod) y los
renderizadores de los restos. La división de la malla en partes es automática: si algo no encaja, se ajusta en
el script. Las alturas de las cabezas y de los cadáveres tumbados son aproximadas y hay que probarlas en el juego.
Si la acción de GitHub o `gradlew build` fallan, pega el mensaje.
