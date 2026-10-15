package com.odan.livingworld.needs;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.HolderSystem;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;

import javax.annotation.Nonnull;

/**
 * Garantit que chaque PNJ entre dans le store avec un NeedsComponent.
 * Un composant restauré depuis la sauvegarde est conservé tel quel : son horodatage
 * permettra au NeedsTickSystem de rattraper le temps passé hors des chunks chargés.
 */
public class NeedsAttachSystem extends HolderSystem<EntityStore> {

    private final ComponentType<EntityStore, NeedsComponent> needsType;

    public NeedsAttachSystem(@Nonnull ComponentType<EntityStore, NeedsComponent> needsType) {
        this.needsType = needsType;
    }

    @Override
    public void onEntityAdd(@Nonnull Holder<EntityStore> holder, @Nonnull AddReason reason, @Nonnull Store<EntityStore> store) {
        NeedsComponent needs = holder.ensureAndGetComponent(needsType);
        if (needs.getLastUpdate() == null) {
            // Nouveau PNJ : on démarre l'horloge à l'heure de jeu courante
            WorldTimeResource time = store.getResource(WorldTimeResource.getResourceType());
            needs.setLastUpdate(time.getGameTime());
        }
    }

    @Override
    public void onEntityRemoved(@Nonnull Holder<EntityStore> holder, @Nonnull RemoveReason reason, @Nonnull Store<EntityStore> store) {
        // Rien à faire : lastUpdate est déjà persisté avec l'entité
    }

    @Override
    public Query<EntityStore> getQuery() {
        return NPCEntity.getComponentType();
    }
}
