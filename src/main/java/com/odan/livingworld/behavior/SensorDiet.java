package com.odan.livingworld.behavior;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.corecomponents.SensorBase;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.instructions.ExecutionSupport;
import com.hypixel.hytale.server.npc.sensorinfo.InfoProvider;

import javax.annotation.Nonnull;

/**
 * Sensor « LivingWorld_Diet » : vrai si le PNJ a le régime attendu. Évalué à chaque tick :
 * lecture seule (cache de DietClassifier), aucun log.
 */
public class SensorDiet extends SensorBase {

    private final Diet diet;

    public SensorDiet(@Nonnull BuilderSensorDiet builder) {
        super(builder);
        this.diet = builder.getDiet();
    }

    @Override
    public boolean matches(@Nonnull Ref<EntityStore> ref, @Nonnull ExecutionSupport support, double dt,
                           @Nonnull Store<EntityStore> store) {
        if (!super.matches(ref, support, dt, store)) {
            return false;
        }
        NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
        String roleName = npc != null ? npc.getRoleName() : null;
        return DietClassifier.classify(support.getRoleIndex(), roleName).matches(diet);
    }

    @Override
    public InfoProvider getSensorInfo() {
        // Pas de cible ni de position : dans un « And », l'info vient de l'autre sensor
        return null;
    }
}
