# LivingWorld

Plugin serveur Hytale (0.7.2) qui simule des besoins de base des PNJ — faim, fatigue — y compris lorsque leurs chunks sont déchargés.

## Prérequis

- JDK 25
- Maven 3.9+ (ou le wrapper Gradle fourni)

## Build

```bash
# Maven
mvn clean package
# -> target/LivingWorld-0.1.0.jar

# Gradle
./gradlew shadowJar
# -> build/libs/LivingWorld-0.1.0.jar
```

Déposer le jar dans le dossier `mods/` du serveur.

## Structure

```
src/main/
├── java/com/odan/livingworld/
│   ├── LivingWorldPlugin.java       # Point d'entrée du plugin
│   ├── command/
│   │   └── LivingWorldCommand.java  # Commande /livingworld
│   ├── needs/                       # NeedsComponent, tick de faim/fatigue, attache aux PNJ
│   └── behavior/                    # Sensors/Actions NPC custom, régime, explications d'activité
└── resources/
    ├── manifest.json                # Métadonnées du plugin
    └── Server/                      # Pack d'assets (surcharges de templates de rôle, BlockSets)
```

## Comportements de faim

Le régime est déduit des groupes NPC vanilla : `Prey` → petit herbivore (et petits des gros animaux), `PreyBig` → gros herbivore, `PredatorsBig` → gros prédateur (loups, ours…), `Predators`/`Vermin` → petit prédateur (renard, araignées, serpents, rats, scorpions).

- **Herbivore**
  - prédateur à moins de 20 blocs → fuite immédiate, y compris s'il est déjà en alerte. Un petit herbivore fuit tous les prédateurs, un gros seulement les gros ;
  - sous 40 de satiété, sans prédateur à moins de 32 blocs ni fuite récente (15-25 s) → part manger tout de suite : plante la plus proche (`LivingWorld_HerbivoreFood`) jusqu'à 32 blocs, rejointe rapidement, mangée sur place sans bouger (animation, particules, +35) ; herbes hautes, fleurs et cultures disparaissent. Si un prédateur approche pendant la recherche, il y renonce et la plante visée est oubliée. Sans plante en vue, il explore 10-14 s puis réessaie 10-15 s plus tard. Sous 20, il se réveille s'il dormait.
- **Prédateur** sous 40 de satiété :
  1. viande au sol (`Food_Wildmeat_Raw`, `Food_Beef_Raw`…) à moins de 24 blocs → va la manger (+30 par morceau) ; c'est le seul moyen de regagner de la satiété ;
  2. proie à moins de 20 blocs (`Prey`/`PreyBig` pour les gros, `Prey` pour les petits) → attaque immédiate ; une fois tuée, elle lâche sa viande ;
  3. proie flairée à moins de 48 blocs → remonte sa piste ;
  4. sinon → patrouille en gardant à peu près sa direction, avec des pauses pour flairer. Son point d'attache (leash) le suit : il quitte réellement sa zone de spawn et s'installe là où il mange. Sous 20, il se réveille s'il dormait.

Les templates `Template_Animal_Neutral`, `Template_Predator` et `Template_Spider` sont des copies du vanilla 0.7.2 générées par `tools/patch-templates.js` ; les ajouts sont les blocs dont le `$Comment` commence par « LivingWorld ». Après chaque mise à jour de Hytale, les régénérer depuis les fichiers vanilla (extraits de `Assets.zip`) :

```bash
R=src/main/resources/Server/NPC/Roles
node tools/patch-templates.js <vanilla>/_Core/Templates/Template_Animal_Neutral.json $R/_Core/Templates/Template_Animal_Neutral.json herbivore
node tools/patch-templates.js <vanilla>/_Core/Templates/Template_Predator.json $R/_Core/Templates/Template_Predator.json predator
node tools/patch-templates.js <vanilla>/Creature/Vermin/Templates/Template_Spider.json $R/Creature/Vermin/Templates/Template_Spider.json spider
```

## Commandes

| Commande       | Effet                                       |
|----------------|---------------------------------------------|
| `/livingworld` | Affiche le nom et la version du plugin      |
| `/livingworld status [--world <nom>]` | Nombre de PNJ chargés avec besoins, satiété et fatigue moyennes |
| `/livingworld debug [--world <nom>]`  | (OP) Force une mise à jour des besoins de tous les PNJ chargés |
| `/livingworld starve [--world <nom>]` | (OP) Met la satiété de tous les PNJ chargés à 20, pour tester les comportements de faim |
| `/livingworld inspect [--entity <uuid>]` | Explique dans le chat ce que fait le PNJ regardé : régime, satiété, fatigue, état du rôle, activité |
| `/livingworld labels [--world <nom>]` | (OP) Active/désactive une étiquette « satiété · activité » au-dessus des PNJ (visible par tous, mise à jour chaque seconde) |
