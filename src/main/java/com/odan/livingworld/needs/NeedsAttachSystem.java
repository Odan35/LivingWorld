package com.odan.livingworld.needs;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.HolderSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.odan.livingworld.debug.NpcLabelSystem;

import javax.annotation.Nonnull;
import java.time.Instant;

/**
 * Garantit que chaque PNJ entre dans le store avec un NeedsComponent.
 * Un composant restauré depuis la sauvegarde est conservé tel quel : son horodatage
 * permettra au NeedsTickSystem de rattraper le temps passé hors des chunks chargés.
 */
public class NeedsAttachSystem extends HolderSystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final ComponentType<EntityStore, NeedsComponent> needsType;

    public NeedsAttachSystem(@Nonnull ComponentType<EntityStore, NeedsComponent> needsType) {
        this.needsType = needsType;
    }

    @Override
    public void onEntityAdd(@Nonnull Holder<EntityStore> holder, @Nonnull AddReason reason, @Nonnull Store<EntityStore> store) {
        removeStaleDebugLabel(holder);
        NeedsComponent needs = holder.ensureAndGetComponent(needsType);
        Instant now = store.getResource(WorldTimeResource.getResourceType()).getGameTime();
        if (needs.getLastUpdate() == null) {
            // Nouveau PNJ : on démarre l'horloge à l'heure de jeu courante
            needs.setLastUpdate(now);
            LOGGER.atInfo().log("NeedsAttachSystem : besoins attachés à %s (%s)", EntityLabels.of(holder), reason);
            return;
        }
        // Composant restauré depuis la sauvegarde : pas de log en régime normal (le rattrapage
        // est logué par NeedsTickSystem), seulement si les données désérialisées sont incohérentes
        validateRestored(holder, needs, now);
    }

    /**
     * Le nameplate est persistant : si le chunk a été sauvegardé pendant que /livingworld labels
     * était actif, l'étiquette de debug a été enregistrée avec le PNJ. On la retire au rechargement.
     * Limite : un PNJ qui avait un vrai nameplate avant l'étiquette le perd dans ce cas.
     */
    private static void removeStaleDebugLabel(@Nonnull Holder<EntityStore> holder) {
        Nameplate nameplate = holder.getComponent(Nameplate.getComponentType());
        if (nameplate != null && nameplate.getText() != null
            && nameplate.getText().startsWith(NpcLabelSystem.LABEL_PREFIX)) {
            holder.removeComponent(Nameplate.getComponentType());
            LOGGER.atWarning().log("NeedsAttachSystem : étiquette de debug sauvegardée retirée de %s",
                EntityLabels.of(holder));
        }
    }

    /** Détecte les valeurs corrompues que le codec a acceptées sans erreur. */
    private static void validateRestored(@Nonnull Holder<EntityStore> holder, @Nonnull NeedsComponent needs,
                                         @Nonnull Instant now) {
        // NaN traverse le clamp des setters : on le remplace par la valeur par défaut
        if (Float.isNaN(needs.getHunger())) {
            LOGGER.atWarning().log("NeedsAttachSystem : satiété invalide (NaN) à la désérialisation de %s, réinitialisée",
                EntityLabels.of(holder));
            needs.setHunger(NeedsComponent.MAX_VALUE);
        }
        if (Float.isNaN(needs.getFatigue())) {
            LOGGER.atWarning().log("NeedsAttachSystem : fatigue invalide (NaN) à la désérialisation de %s, réinitialisée",
                EntityLabels.of(holder));
            needs.setFatigue(NeedsComponent.MIN_VALUE);
        }
        if (needs.getLastUpdate().isAfter(now)) {
            // Sauvegarde venant d'un autre monde ou horloge modifiée : le tick suivant ne rattrapera rien
            LOGGER.atWarning().log("NeedsAttachSystem : horodatage de %s dans le futur (%s > %s), pas de rattrapage",
                EntityLabels.of(holder), needs.getLastUpdate(), now);
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
