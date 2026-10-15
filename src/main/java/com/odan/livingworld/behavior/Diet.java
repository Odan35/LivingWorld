package com.odan.livingworld.behavior;

import java.util.function.Supplier;

/**
 * Régime alimentaire d'un PNJ, déduit des groupes NPC vanilla (voir DietClassifier).
 * Implémente Supplier pour servir de paramètre enum dans les fichiers Role JSON.
 *
 * Herbivore et Carnivore sont des filtres pour les sensors (« tout herbivore », « tout
 * prédateur ») : la classification ne les renvoie jamais.
 */
public enum Diet implements Supplier<String> {

    SmallHerbivore("Petit herbivore ou petit d'animal (groupe Prey) : fuit tous les prédateurs"),
    LargeHerbivore("Gros herbivore (groupe PreyBig) : ne fuit que les gros prédateurs"),
    Predator("Gros prédateur : chasse Prey et PreyBig (groupe PredatorsBig)"),
    SmallPredator("Petit prédateur : chasse Prey (groupes Predators, Vermin)"),
    Herbivore("Tout herbivore, petit ou gros (filtre de sensor)"),
    Carnivore("Tout prédateur, grand ou petit (filtre de sensor)"),
    None("Aucun comportement alimentaire");

    private final String description;

    Diet(String description) {
        this.description = description;
    }

    @Override
    public String get() {
        return description;
    }

    public boolean isHerbivore() {
        return this == SmallHerbivore || this == LargeHerbivore;
    }

    public boolean isCarnivore() {
        return this == Predator || this == SmallPredator;
    }

    /** Vrai si un PNJ de ce régime satisfait le filtre {@code filter} d'un sensor. */
    public boolean matches(Diet filter) {
        return switch (filter) {
            case Herbivore -> isHerbivore();
            case Carnivore -> isCarnivore();
            default -> this == filter;
        };
    }
}
