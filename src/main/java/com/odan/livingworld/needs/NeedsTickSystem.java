package com.odan.livingworld.needs;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Fait évoluer faim et fatigue à chaque tick.
 *
 * Plutôt que d'utiliser le deltaTime du tick, on calcule le temps de jeu écoulé depuis
 * NeedsComponent.lastUpdate. En régime normal cela revient au même ; mais au premier
 * tick qui suit le rechargement d'un chunk, l'écart couvre toute la période où le PNJ
 * était déchargé, ce qui simule ses besoins « hors ligne » sans aucun traitement à part.
 *
 * Logs : aucun log par tick, seulement les rattrapages hors chunk et les changements
 * d'état (affamé / épuisé).
 */
public class NeedsTickSystem extends EntityTickingSystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final double NANOS_PER_HOUR = 3_600_000_000_000.0;
    /**
     * Sous ce seuil de satiété, le PNJ est considéré comme affamé. Aligné sur le paramètre
     * LivingWorldHungerThreshold des templates de rôle, qui déclenche la recherche de nourriture.
     */
    public static final float HUNGRY_THRESHOLD = 40.0f;
    /** Au-dessus de ce seuil de fatigue, le PNJ est considéré comme épuisé. */
    public static final float TIRED_THRESHOLD = 70.0f;
    /** Un écart plus long qu'un tick normal ne peut venir que d'un passage hors chunk chargé. */
    private static final Duration CATCH_UP_LOG_THRESHOLD = Duration.ofMinutes(30);

    private final ComponentType<EntityStore, NeedsComponent> needsType;
    /** Perte de satiété par heure de jeu. */
    private final float hungerPerHour;
    /** Gain de fatigue par heure de jeu. */
    private final float fatiguePerHour;

    public NeedsTickSystem(@Nonnull ComponentType<EntityStore, NeedsComponent> needsType,
                           float hungerPerHour, float fatiguePerHour) {
        this.needsType = needsType;
        this.hungerPerHour = hungerPerHour;
        this.fatiguePerHour = fatiguePerHour;
    }

    @Override
    public void tick(float deltaTime, int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
                     @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> buffer) {
        Instant now = store.getResource(WorldTimeResource.getResourceType()).getGameTime();
        simulate(chunk, index, now);
    }

    /**
     * Applique l'évolution des besoins jusqu'à {@code now}. Appelée par le tick normal
     * et par /livingworld debug pour forcer une mise à jour.
     */
    public void simulate(@Nonnull ArchetypeChunk<EntityStore> chunk, int index, @Nonnull Instant now) {
        NeedsComponent needs = chunk.getComponent(index, needsType);

        Instant last = needs.getLastUpdate();
        needs.setLastUpdate(now);
        if (last == null) {
            // Ne devrait pas arriver : NeedsAttachSystem initialise l'horloge à l'arrivée du PNJ
            LOGGER.atWarning().atMostEvery(30, TimeUnit.SECONDS).log(
                "NeedsTickSystem : %s sans horodatage, horloge initialisée sans rattrapage",
                EntityLabels.of(chunk, index));
            return;
        }
        if (!now.isAfter(last)) {
            if (now.isBefore(last)) {
                // Horloge du monde revenue en arrière (commande /time) : touche tous les PNJ d'un coup
                LOGGER.atWarning().atMostEvery(30, TimeUnit.SECONDS).log(
                    "NeedsTickSystem : heure de jeu revenue en arrière (%s -> %s), besoins figés pour ce tick",
                    last, now);
            }
            return;
        }

        boolean wasHungry = needs.isHungry(HUNGRY_THRESHOLD);
        boolean wasTired = needs.isTired(TIRED_THRESHOLD);
        float hungerBefore = needs.getHunger();
        float fatigueBefore = needs.getFatigue();

        Duration elapsed = Duration.between(last, now);
        double hours = elapsed.toNanos() / NANOS_PER_HOUR;
        // Les setters bornent les valeurs à [0, 100], ce qui borne aussi les longs rattrapages
        needs.setHunger((float) (needs.getHunger() - hungerPerHour * hours));
        needs.setFatigue((float) (needs.getFatigue() + fatiguePerHour * hours));

        if (elapsed.compareTo(CATCH_UP_LOG_THRESHOLD) > 0) {
            LOGGER.atInfo().log(String.format(Locale.ROOT,
                "NeedsTickSystem : rattrapage hors chunk de %.1f h pour %s (satiété %.1f -> %.1f, fatigue %.1f -> %.1f)",
                hours, EntityLabels.of(chunk, index),
                hungerBefore, needs.getHunger(), fatigueBefore, needs.getFatigue()));
        }
        logTransitions(chunk, index, needs, wasHungry, wasTired);
    }

    /** Ne logue que les franchissements de seuil, jamais l'évolution continue. */
    private static void logTransitions(@Nonnull ArchetypeChunk<EntityStore> chunk, int index,
                                       @Nonnull NeedsComponent needs, boolean wasHungry, boolean wasTired) {
        boolean hungry = needs.isHungry(HUNGRY_THRESHOLD);
        boolean tired = needs.isTired(TIRED_THRESHOLD);
        if (hungry != wasHungry) {
            LOGGER.atInfo().log(String.format(Locale.ROOT, "NeedsTickSystem : %s %s (satiété %.1f)",
                EntityLabels.of(chunk, index), hungry ? "devient affamé" : "n'est plus affamé", needs.getHunger()));
        }
        if (tired != wasTired) {
            LOGGER.atInfo().log(String.format(Locale.ROOT, "NeedsTickSystem : %s %s (fatigue %.1f)",
                EntityLabels.of(chunk, index), tired ? "devient épuisé" : "n'est plus épuisé", needs.getFatigue()));
        }
    }

    @Override
    public Query<EntityStore> getQuery() {
        return needsType;
    }
}
