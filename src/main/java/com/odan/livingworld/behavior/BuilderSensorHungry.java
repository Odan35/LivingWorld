package com.odan.livingworld.behavior;

import com.google.gson.JsonElement;
import com.hypixel.hytale.server.npc.asset.builder.Builder;
import com.hypixel.hytale.server.npc.asset.builder.BuilderDescriptorState;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.asset.builder.holder.DoubleHolder;
import com.hypixel.hytale.server.npc.asset.builder.validators.DoubleRangeValidator;
import com.hypixel.hytale.server.npc.corecomponents.builders.BuilderSensorBase;
import com.hypixel.hytale.server.npc.instructions.Sensor;
import com.odan.livingworld.needs.NeedsComponent;

import javax.annotation.Nonnull;

/**
 * Lit la configuration JSON du sensor « LivingWorld_Hungry » :
 * <pre>{ "Type": "LivingWorld_Hungry", "Diet": "Herbivore", "Threshold": 40 }</pre>
 */
public class BuilderSensorHungry extends BuilderSensorBase {

    public static final String TYPE = "LivingWorld_Hungry";

    protected Diet diet = Diet.None;
    /** Holder plutôt que double : accepte { "Compute": "..." } depuis les paramètres du template. */
    protected final DoubleHolder threshold = new DoubleHolder();

    @Override
    public Sensor build(BuilderSupport support) {
        return new SensorHungry(this, support);
    }

    @Override
    public String getShortDescription() {
        return "Vrai si le PNJ a le régime donné et une satiété sous le seuil (LivingWorld)";
    }

    @Override
    public String getLongDescription() {
        return "Lit le NeedsComponent de LivingWorld. Le régime est déduit des groupes NPC vanilla "
            + "(Prey = SmallHerbivore, PreyBig = LargeHerbivore, PredatorsBig = Predator, Predators/Vermin = SmallPredator). "
            + "Herbivore et Carnivore acceptent les deux tailles.";
    }

    @Override
    public BuilderDescriptorState getBuilderDescriptorState() {
        return BuilderDescriptorState.Stable;
    }

    @Override
    public Builder<Sensor> readConfig(JsonElement data) {
        this.requireEnum(data, "Diet", value -> this.diet = value, Diet.class,
            BuilderDescriptorState.Stable, "Régime auquel ce sensor réagit", null);
        this.getDouble(data, "Threshold", this.threshold, 40.0,
            DoubleRangeValidator.between(NeedsComponent.MIN_VALUE, NeedsComponent.MAX_VALUE),
            BuilderDescriptorState.Stable, "Seuil de satiété sous lequel le PNJ a faim", null);
        return this;
    }

    @Nonnull
    public Diet getDiet() {
        return diet;
    }

    public double getThreshold(@Nonnull BuilderSupport support) {
        return threshold.get(support.getExecutionContext());
    }
}
