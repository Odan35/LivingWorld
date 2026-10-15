package com.odan.livingworld.needs;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.time.Duration;
import java.time.Instant;

/**
 * Fait évoluer faim et fatigue à chaque tick.
 *
 * Plutôt que d'utiliser le deltaTime du tick, on calcule le temps de jeu écoulé depuis
 * NeedsComponent.lastUpdate. En régime normal cela revient au même ; mais au premier
 * tick qui suit le rechargement d'un chunk, l'écart couvre toute la période où le PNJ
 * était déchargé, ce qui simule ses besoins « hors ligne » sans aucun traitement à part.
 */
public class NeedsTickSystem extends EntityTickingSystem<EntityStore> {

    private static final double NANOS_PER_HOUR = 3_600_000_000_000.0;

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
        NeedsComponent needs = chunk.getComponent(index, needsType);
        Instant now = store.getResource(WorldTimeResource.getResourceType()).getGameTime();

        Instant last = needs.getLastUpdate();
        needs.setLastUpdate(now);
        if (last == null || !now.isAfter(last)) {
            // Composant non initialisé, ou horloge du monde revenue en arrière (commande /time)
            return;
        }

        double hours = Duration.between(last, now).toNanos() / NANOS_PER_HOUR;
        // Les setters bornent les valeurs à [0, 100], ce qui borne aussi les longs rattrapages
        needs.setHunger((float) (needs.getHunger() - hungerPerHour * hours));
        needs.setFatigue((float) (needs.getFatigue() + fatiguePerHour * hours));
    }

    @Override
    public Query<EntityStore> getQuery() {
        return needsType;
    }
}
