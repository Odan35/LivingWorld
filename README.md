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
│   └── command/
│       └── LivingWorldCommand.java  # Commande /livingworld
└── resources/
    └── manifest.json                # Métadonnées du plugin
```

## Commandes

| Commande       | Effet                                       |
|----------------|---------------------------------------------|
| `/livingworld` | Affiche le nom et la version du plugin      |
