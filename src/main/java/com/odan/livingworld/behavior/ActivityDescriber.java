package com.odan.livingworld.behavior;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.support.StateSupport;
import com.odan.livingworld.needs.NeedsComponent;
import com.odan.livingworld.needs.NeedsTickSystem;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Traduit l'état d'un PNJ (état du rôle, besoins, régime) en texte lisible, pour
 * /livingworld inspect et les étiquettes au-dessus des PNJ. Lecture seule.
 *
 * Les états viennent des templates de rôle : « Idle.LivingWorldForage » est notre sous-état
 * de recherche de nourriture, les autres sont vanilla (Idle, Alerted, Combat, Sleep…).
 */
public final class ActivityDescriber {

    /** Activité résumée : un libellé court pour l'étiquette, des phrases pour l'inspection. */
    public record Activity(@Nonnull String shortLabel, @Nonnull List<String> details) {
    }

    private ActivityDescriber() {
    }

    @Nonnull
    public static Activity describe(@Nonnull Ref<EntityStore> ref, @Nonnull ComponentAccessor<EntityStore> accessor) {
        NPCEntity npc = accessor.getComponent(ref, NPCEntity.getComponentType());
        NeedsComponent needs = accessor.getComponent(ref, NeedsComponent.getComponentType());
        StateSupport stateSupport = accessor.getComponent(ref, StateSupport.getComponentType());
        String state = stateSupport != null ? stateSupport.getStateName() : null;
        Diet diet = npc != null ? DietClassifier.classify(npc.getRoleIndex(), npc.getRoleName()) : Diet.None;
        boolean hungry = needs != null && needs.isHungry(NeedsTickSystem.HUNGRY_THRESHOLD);

        List<String> details = new ArrayList<>();
        details.add("Rôle : " + (npc != null ? npc.getRoleName() : "?") + " — régime : " + dietLabel(diet));
        if (needs != null) {
            details.add(String.format(Locale.ROOT, "Satiété : %.1f / 100%s — fatigue : %.1f / 100",
                needs.getHunger(), hungry ? " (affamé, seuil " + (int) NeedsTickSystem.HUNGRY_THRESHOLD + ")" : "",
                needs.getFatigue()));
        } else {
            details.add("Pas de besoins LivingWorld sur ce PNJ.");
        }
        details.add("État du rôle : " + (state != null ? state : "inconnu"));

        String label = activity(state, diet, hungry, details);
        return new Activity(label, details);
    }

    /** Déduit l'activité de l'état du rôle et ajoute l'explication correspondante. */
    @Nonnull
    private static String activity(@Nullable String state, @Nonnull Diet diet, boolean hungry,
                                   @Nonnull List<String> details) {
        if (state == null) {
            details.add("→ État inconnu : le rôle n'est peut-être pas encore initialisé.");
            return "?";
        }
        if (state.contains("LivingWorldForage")) {
            details.add("→ Cherche à manger : affamé, il va vers la plante la plus proche visible (herbe haute, "
                + "fleur, culture, jusqu'à 32 blocs) ou explore s'il n'en voit pas. La plante mangée disparaît.");
            return "cherche à manger";
        }
        if (state.contains("LivingWorldScavenge")) {
            details.add("→ Va manger de la viande tombée au sol (proie tuée). C'est ce repas qui lui rend de la satiété.");
            return "mange de la viande";
        }
        if (state.contains("LivingWorldTrack")) {
            details.add("→ Suit une piste : il a flairé une proie et s'en rapproche. Dès qu'elle est à portée "
                + "de chasse, il l'attaque.");
            return "suit une piste";
        }
        if (state.contains("LivingWorldProwl")) {
            details.add("→ Patrouille : affamé et sans proie en vue, il voyage en gardant sa direction et s'arrête "
                + "pour flairer. Sa zone le suit : il quitte son spawn et s'installera là où il mangera.");
            return "patrouille (chasse)";
        }
        if (state.contains("FindFood")) {
            details.add("→ Comportement vanilla : va grignoter du foin ou un objet qui l'attire (indépendant de la faim).");
            return "va grignoter (vanilla)";
        }
        if (state.startsWith("Combat")) {
            if (diet.isCarnivore() && hungry) {
                details.add("→ Chasse : affamé, il poursuit une proie. S'il la tue, il mangera la viande qu'elle lâche.");
                return "chasse une proie";
            }
            details.add("→ En combat (attaqué ou menacé), sans lien avec la faim.");
            return "combat";
        }
        if (state.startsWith("Sleep")) {
            details.add(diet == Diet.None
                ? "→ Dort ou fait une sieste."
                : "→ Dort : il se réveille de lui-même pour manger si sa satiété passe sous 20.");
            return "dort";
        }
        if (state.startsWith("Alerted")) {
            details.add("→ En alerte : une menace ou un joueur a été détecté, il surveille avant de réagir.");
            return "en alerte";
        }
        if (state.startsWith("Flee") || state.startsWith("Panic")) {
            details.add(diet.isHerbivore()
                ? "→ Fuit un danger : un prédateur repéré (jusqu'à 20 blocs) ou une attaque."
                : "→ Fuit un danger.");
            return "fuit";
        }
        if (state.startsWith("ReturnHome")) {
            details.add("→ S'est trop éloigné de sa zone et y retourne.");
            return "rentre";
        }
        if (state.startsWith("Search")) {
            details.add("→ Cherche une cible qu'il a perdue de vue.");
            return "cherche une cible";
        }
        if (state.startsWith("Idle")) {
            return idle(diet, hungry, details);
        }
        details.add("→ État non reconnu par LivingWorld.");
        return state;
    }

    @Nonnull
    private static String idle(@Nonnull Diet diet, boolean hungry, @Nonnull List<String> details) {
        if (!hungry) {
            details.add(diet == Diet.None
                ? "→ Au repos ou se promène. Ce rôle n'a pas de comportement de faim LivingWorld."
                : "→ Au repos ou se promène : rassasié, il ne cherchera à manger que sous "
                    + (int) NeedsTickSystem.HUNGRY_THRESHOLD + " de satiété.");
            return "au repos";
        }
        if (diet.isHerbivore()) {
            details.add("→ Affamé mais pas en train de manger : un prédateur rôde à moins de 32 blocs, "
                + "il vient de fuir (15 à 25 s de méfiance) ou il réessaiera bientôt après une recherche vaine.");
        } else if (diet.isCarnivore()) {
            details.add("→ Affamé : va chercher de la viande, une proie ou partir en patrouille au prochain tick du rôle.");
        } else {
            details.add("→ Affamé, mais ce rôle n'a pas de comportement de faim LivingWorld.");
        }
        return "affamé";
    }

    @Nonnull
    private static String dietLabel(@Nonnull Diet diet) {
        return switch (diet) {
            case SmallHerbivore -> "petit herbivore (fuit tous les prédateurs)";
            case LargeHerbivore -> "gros herbivore (fuit les gros prédateurs)";
            case Herbivore -> "herbivore";
            case Predator -> "gros prédateur (chasse Prey et PreyBig)";
            case SmallPredator -> "petit prédateur (chasse Prey)";
            case Carnivore -> "prédateur";
            case None -> "aucun (pas de comportement de faim)";
        };
    }
}
