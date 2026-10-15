package com.odan.livingworld.behavior;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefChangeSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.odan.livingworld.needs.EntityLabels;

import javax.annotation.Nonnull;

/**
 * Logue les proies tuées par un prédateur (grand ou petit, voir DietClassifier).
 * La mise à mort ne nourrit pas : le prédateur doit ensuite manger la viande lâchée au sol
 * (instructions .LivingWorldScavenge des templates de prédateurs, action LivingWorld_Eat).
 */
public class PredatorKillSystem extends RefChangeSystem<EntityStore, DeathComponent> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    @Override
    public ComponentType<EntityStore, DeathComponent> componentType() {
        return DeathComponent.getComponentType();
    }

    @Override
    public void onComponentAdded(@Nonnull Ref<EntityStore> victim, @Nonnull DeathComponent death,
                                 @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> buffer) {
        Damage damage = death.getDeathInfo();
        if (damage == null || !(damage.getSource() instanceof Damage.EntitySource source)) {
            return;
        }
        Ref<EntityStore> killer = source.getRef();
        if (killer == null || !killer.isValid()) {
            return;
        }
        NPCEntity killerNpc = store.getComponent(killer, NPCEntity.getComponentType());
        if (killerNpc == null
            || !DietClassifier.classify(killerNpc.getRoleIndex(), killerNpc.getRoleName()).isCarnivore()) {
            return;
        }
        // Ne logger que les vraies proies (Prey/PreyBig = herbivores). Les combats hors chasse
        // (prédateur vs prédateur, araignée vs squelette...) ne font pas tomber de viande
        // au sens LivingWorld et le message « la viande tombera au sol » serait trompeur.
        NPCEntity victimNpc = store.getComponent(victim, NPCEntity.getComponentType());
        if (victimNpc == null
            || !DietClassifier.classify(victimNpc.getRoleIndex(), victimNpc.getRoleName()).isHerbivore()) {
            return;
        }
        LOGGER.atInfo().log("PredatorKillSystem : %s a tué %s, la viande tombera au sol",
            EntityLabels.of(killer, store), EntityLabels.of(victim, store));
    }

    @Override
    public void onComponentSet(@Nonnull Ref<EntityStore> ref, DeathComponent previous, @Nonnull DeathComponent current,
                               @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> buffer) {
        // Une seule mort compte : log à l'ajout du composant
    }

    @Override
    public void onComponentRemoved(@Nonnull Ref<EntityStore> ref, @Nonnull DeathComponent death,
                                   @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> buffer) {
        // Rien à faire
    }

    @Override
    public Query<EntityStore> getQuery() {
        // Seule la mort d'un PNJ compte (pas celle d'un joueur)
        return NPCEntity.getComponentType();
    }
}
