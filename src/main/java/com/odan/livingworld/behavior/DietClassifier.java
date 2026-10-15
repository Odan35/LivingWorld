package com.odan.livingworld.behavior;

import com.hypixel.hytale.builtin.tagset.config.NPCGroup;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.npc.role.support.WorldSupport;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Détermine le régime d'un rôle NPC à partir des groupes vanilla (Server/NPC/Groups) :
 * PredatorsBig → gros prédateur (loups, ours…), Predators/Vermin → petit prédateur
 * (renard, araignées, serpents, rats, scorpions), Prey → petit herbivore, PreyBig → gros
 * herbivore, sinon aucun.
 * Aucune espèce n'est listée en dur : un rôle ajouté à ces groupes (par Hytale ou un autre
 * mod) est pris en compte automatiquement.
 *
 * Le résultat est mis en cache par index de rôle. Les rôles ne sont évalués que depuis
 * le thread d'un monde, mais plusieurs mondes tournent en parallèle : accès synchronisé.
 */
public final class DietClassifier {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final String[] SMALL_HERBIVORE_GROUPS = { "Prey" };
    private static final String[] LARGE_HERBIVORE_GROUPS = { "PreyBig" };
    private static final String[] BIG_PREDATOR_GROUPS = { "PredatorsBig" };
    private static final String[] SMALL_PREDATOR_GROUPS = { "Predators", "Vermin" };
    /** Valeur renvoyée par l'asset map quand un groupe n'existe pas. */
    private static final int MISSING = Integer.MIN_VALUE;

    private static final Int2ObjectMap<Diet> CACHE = new Int2ObjectOpenHashMap<>();

    private DietClassifier() {
    }

    /**
     * @param roleName nom du rôle, uniquement pour le log lors du premier classement
     */
    @Nonnull
    public static synchronized Diet classify(int roleIndex, @Nullable String roleName) {
        Diet cached = CACHE.get(roleIndex);
        if (cached != null) {
            return cached;
        }
        Diet diet = Diet.None;
        // Ordre important : un rôle présent dans plusieurs groupes prend le régime le plus carnivore
        if (inAnyGroup(roleIndex, BIG_PREDATOR_GROUPS)) {
            diet = Diet.Predator;
        } else if (inAnyGroup(roleIndex, SMALL_PREDATOR_GROUPS)) {
            diet = Diet.SmallPredator;
        } else if (inAnyGroup(roleIndex, SMALL_HERBIVORE_GROUPS)) {
            // Prey contient aussi les petits des gros animaux (Cow_Calf, Sheep_Lamb…) : vulnérables à tous
            diet = Diet.SmallHerbivore;
        } else if (inAnyGroup(roleIndex, LARGE_HERBIVORE_GROUPS)) {
            diet = Diet.LargeHerbivore;
        }
        CACHE.put(roleIndex, diet);
        // Un log par rôle et par démarrage du serveur : permet de vérifier la classification
        LOGGER.atInfo().log("DietClassifier : rôle %s classé %s", roleName != null ? roleName : "#" + roleIndex, diet);
        return diet;
    }

    /** Vide le cache, par exemple après un rechargement des assets. */
    public static synchronized void clear() {
        CACHE.clear();
    }

    private static boolean inAnyGroup(int roleIndex, @Nonnull String[] groups) {
        for (String group : groups) {
            int groupIndex = NPCGroup.getAssetMap().getIndexOrDefault(group, MISSING);
            if (groupIndex == MISSING) {
                // Groupe vanilla renommé ou supprimé : le régime ne pourra plus être détecté
                LOGGER.atWarning().log("DietClassifier : groupe NPC %s introuvable", group);
                continue;
            }
            if (WorldSupport.hasTagInGroup(groupIndex, roleIndex)) {
                return true;
            }
        }
        return false;
    }
}
