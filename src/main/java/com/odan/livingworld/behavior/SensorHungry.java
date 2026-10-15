package com.odan.livingworld.behavior;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.SensorBase;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.instructions.ExecutionSupport;
import com.hypixel.hytale.server.npc.sensorinfo.InfoProvider;
import com.odan.livingworld.needs.NeedsComponent;

import javax.annotation.Nonnull;

/**
 * Sensor « LivingWorld_Hungry » : vrai si le PNJ a le régime attendu et une satiété sous le seuil.
 * Évalué à chaque tick du rôle : lecture seule et aucun log ici (les transitions sont loguées
 * par l'action LivingWorld_NeedsEvent et par NeedsTickSystem).
 */
public class SensorHungry extends SensorBase {

    private final Diet diet;
    private final double threshold;

    public SensorHungry(@Nonnull BuilderSensorHungry builder, @Nonnull BuilderSupport support) {
        super(builder);
        this.diet = builder.getDiet();
        this.threshold = builder.getThreshold(support);
    }

    @Override
    public boolean matches(@Nonnull Ref<EntityStore> ref, @Nonnull ExecutionSupport support, double dt,
                           @Nonnull Store<EntityStore> store) {
        if (!super.matches(ref, support, dt, store)) {
            return false;
        }
        if (!DietClassifier.classify(support.getRoleIndex(), roleName(ref, store)).matches(diet)) {
            return false;
        }
        NeedsComponent needs = store.getComponent(ref, NeedsComponent.getComponentType());
        return needs != null && needs.getHunger() < threshold;
    }

    @Override
    public InfoProvider getSensorInfo() {
        // Pas de cible ni de position : dans un « And », l'info vient de l'autre sensor
        return null;
    }

    private static String roleName(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
        return npc != null ? npc.getRoleName() : null;
    }
}
