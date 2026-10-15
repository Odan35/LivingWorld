package com.odan.livingworld.behavior;

import com.google.gson.JsonElement;
import com.hypixel.hytale.server.npc.asset.builder.Builder;
import com.hypixel.hytale.server.npc.asset.builder.BuilderDescriptorState;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.builders.BuilderSensorBase;
import com.hypixel.hytale.server.npc.instructions.Sensor;

import javax.annotation.Nonnull;

/**
 * Lit la configuration JSON du sensor « LivingWorld_Diet » :
 * <pre>{ "Type": "LivingWorld_Diet", "Diet": "SmallHerbivore" }</pre>
 * Vrai si le PNJ a ce régime, quelle que soit sa faim (sert par exemple à la fuite des proies).
 */
public class BuilderSensorDiet extends BuilderSensorBase {

    public static final String TYPE = "LivingWorld_Diet";

    protected Diet diet = Diet.None;

    @Override
    public Sensor build(BuilderSupport support) {
        return new SensorDiet(this);
    }

    @Override
    public String getShortDescription() {
        return "Vrai si le PNJ a le régime donné (LivingWorld)";
    }

    @Override
    public String getLongDescription() {
        return "Régime déduit des groupes NPC vanilla. Herbivore et Carnivore acceptent les deux tailles.";
    }

    @Override
    public BuilderDescriptorState getBuilderDescriptorState() {
        return BuilderDescriptorState.Stable;
    }

    @Override
    public Builder<Sensor> readConfig(JsonElement data) {
        this.requireEnum(data, "Diet", value -> this.diet = value, Diet.class,
            BuilderDescriptorState.Stable, "Régime auquel ce sensor réagit", null);
        return this;
    }

    @Nonnull
    public Diet getDiet() {
        return diet;
    }
}
