# Rapport d'analyse des logs — LivingWorld

- **Session analysée** : 15/10/2026, 16:45 → 16:52 (heure locale ; 14:45 → 14:52 dans le log)
- **Fichier** : `%APPDATA%\Hytale\UserData\Saves\Nouveau monde\logs\2026-10-15_16-45-14_server.log`
- **Build testé** : premier lancement avec le jar construit à 16:34 (fuite des herbivores, méfiance, repas immobile, viande pour les prédateurs)
- **Contexte de test** : étiquettes activées (`/livingworld labels`), puis 6 `/livingworld starve` sur 111 à 147 PNJ

## Bilan rapide

| Domaine | État |
|---|---|
| Chargement du plugin et des 3 templates | ✅ OK, aucune erreur |
| Herbivores : chercher et manger | ✅ Fonctionne (127 repas) |
| Herbivores : fuite | ⚠️ Fonctionne, mais fuit en boucle |
| Prédateurs : patrouille, piste, attaque | ✅ Fonctionne |
| Prédateurs : manger la viande | ❌ 1 repas sur 27 tentatives |
| Performances | ✅ Aucun impact visible |

## 1. Démarrage

- Les 4 composants NPC custom (`LivingWorld_Hungry`, `LivingWorld_Diet`, `LivingWorld_NeedsEvent`, `LivingWorld_Eat`) et la commande (status, debug, starve, inspect, labels) sont enregistrés.
- Les 3 templates sont bien remplacés par ceux du mod : `Replacing asset 'Template_Predator' / 'Template_Animal_Neutral' / 'Template_Spider'`. Aucune erreur de validation de rôle.
- Aucune erreur `SEVERE` ni exception liée au mod.
- **311 WARN « étiquette de debug sauvegardée retirée »** au chargement : la session précédente s'est fermée avec `/livingworld labels` actif. Le nettoyage fonctionne, mais il remplit le log. Cette session s'est aussi terminée avec les étiquettes actives, donc ce sera pareil au prochain lancement.

## 2. Herbivores

| Événement | Nombre |
|---|---|
| Commence à chercher de la nourriture | 182 |
| A fini de manger (plante consommée) | **127** (70 %) |
| Renonce : prédateur proche ou fuite récente | 40 |
| Se réveille affamé | 14 |
| Abandonne faute de plante | 1 |

- **La recherche marche bien** : 70 % des recherches aboutissent à un repas, et une seule a échoué faute d'herbe. Chaque repas a bien retiré la plante. Répartition : sanglier 50, lapin (Bunny) 23, Tetrabird 12, mouton 12…
- **Le garde-fou « prédateur proche » fonctionne** : 40 renoncements.
- **Problème : la fuite tourne en boucle.**
  - 270 fuites pour seulement 55 animaux.
  - Exemple : `Goat_Kid 9fe155b0…` a fui **25 fois en 2 minutes**, environ toutes les 4 s, et n'a jamais mangé.
  - Par espèce : sanglier 81, chevrette 40, marcassin 35, lapin (Rabbit) 32, chèvre 29, lapin (Bunny) 19, mouton 17, vache 13.
  - **Cause** : l'état `Flee` vanilla se termine au bout de quelques secondes. Si le prédateur est encore à moins de 20 blocs, la fuite repart aussitôt.
  - **Conséquences** : des rafales de lignes dans le log, et des proies qui ne mangent jamais tant qu'un prédateur traîne dans la zone. Il y avait au moins 6 ours dans cette zone.

## 3. Prédateurs

| Événement | Nombre |
|---|---|
| Part en patrouille | 217 |
| Flaire une proie et remonte sa piste | 142 |
| Repère une proie et l'attaque | 186 (31 prédateurs différents) |
| Mises à mort | 39 |
| Repère de la viande | 27 |
| Ne trouve plus la viande | 20 |
| **A mangé de la viande** | **1** |

- **La chaîne patrouille → piste → attaque fonctionne.** Exemple typique : « part en patrouille », puis « flaire une proie » 1 s plus tard, puis « l'attaque » 1 s après.
- **La chasse réussit peu** : 186 attaques pour environ 29 proies tuées.
  - Le même ours relance une attaque toutes les 7 à 10 s (jusqu'à 15 fois).
  - **Cause** : les proies fuient dès 20 blocs, exactement la distance d'attaque (`LivingWorldHuntRange` = 20). La proie est déjà partie quand la poursuite commence ; le combat s'arrête, puis reprend.
- **Manger la viande ne marche presque pas : 1 repas sur 27.**
  - Le scénario est toujours le même : mise à mort → environ 8 s → « repère de la viande » → 5 à 13 s → « ne trouve plus la viande ».
  - Le seul repas réussi (`Bear_Grizzly 88860c0f…`, satiété 19.8 → 49.8) prouve que le mécanisme marche quand l'ours atteint la viande.
  - Le log ne permet pas de trancher entre deux hypothèses :
    - **la viande est ramassée par le joueur** : un joueur ramasse automatiquement les objets à proximité, et j'étais sûrement près de la scène pour observer ;
    - **l'ours n'arrive pas assez près** : il doit être à 1,6 bloc pour manger, alors qu'un ours est large. Il resterait bloqué juste à côté jusqu'à ce que la viande disparaisse (ramassée ou expirée).
- **Mises à mort hors chasse** : `PredatorKillSystem` compte aussi des combats qui ne sont pas des chasses (ours → araignée, araignée → ours, araignée → squelette). Le message « la viande tombera au sol » est alors trompeur.

## 4. Performances

Aucune alerte de lenteur liée aux PNJ ou au tick des mondes dans les 4 dernières sessions. Toutes les alertes « Took too long » concernent le chargement des chunks, comme en vanilla :

| Session (heure du log) | Alertes | Liées aux PNJ |
|---|---|---|
| 13:19 → 13:26 | 4 | 0 |
| 13:51 → 14:04 | 6 | 0 |
| 14:23 → 14:32 | 20 | 0 |
| 14:45 → 14:52 | 8 | 0 |

Les sensors à grande portée (32 et 48 blocs) n'ont pas d'impact visible.

## 5. Note de lecture

Les 6 `/livingworld starve` remettent la satiété à 20 à chaque fois. C'est pour ça qu'un ours passe de 49.8 après son repas à 19.8 neuf secondes plus tard, et qu'aucun prédateur n'a jamais été rassasié pendant la session : 0 « arrête de chasser ».

## 6. Corrections proposées (non appliquées)

1. **Viande** : passer la portée de repas de 1,6 à 2,5 blocs et ajouter au log la position de la viande. Tester aussi en restant à distance, pour écarter le ramassage par le joueur.
2. **Fuite en boucle** : pendant la méfiance (15-25 s), ne relancer la fuite que si le prédateur approche à moins de 10 blocs, et ne la loguer qu'une fois par épisode.
3. **Chasse plus efficace** : approche lente et discrète jusqu'à environ 10 blocs, puis charge. Ou, plus simple, une portée d'attaque (24-28 blocs) supérieure à la distance de fuite (20).
4. **Log des mises à mort** : ne loguer que les vraies proies (Prey/PreyBig), pas les combats.
5. **Étiquettes** : les désactiver automatiquement avant la sauvegarde, ou regrouper le nettoyage en une seule ligne de log.

## 7. Pour reprendre sur un autre PC

- **Prérequis** : JDK 25, Hytale installé (le build va chercher `HytaleServer.jar` dans l'installation), Node.js pour `tools/patch-templates.js`.
- **Build** : `./gradlew shadowJar`, puis copier `build/libs/LivingWorld-0.1.0.jar` dans `%APPDATA%\Hytale\UserData\Mods\`.
- **Templates de rôle** : ils sont générés depuis les fichiers vanilla. Pour les régénérer, extraire `Server/NPC/Roles/...` de `Assets.zip` dans un dossier temporaire, puis lancer les commandes `node tools/patch-templates.js …` du README. Les copies vanilla utilisées jusqu'ici étaient dans un dossier temporaire de l'ancien PC : il faudra les réextraire.
- **Tests en jeu** : `/livingworld labels` (étiquettes au-dessus des PNJ), `/livingworld starve` (affame les PNJ chargés), `/livingworld inspect` (en regardant un PNJ). Désactiver les étiquettes avant de quitter le monde.
