package com.odan.livingworld.needs;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.odan.livingworld.LivingWorldPlugin;

import javax.annotation.Nullable;
import java.time.Instant;

/**
 * Besoins de base d'un PNJ. Composant persistant : il est sérialisé avec l'entité
 * lorsque son chunk est sauvegardé ou déchargé, puis restauré au rechargement.
 */
public class NeedsComponent implements Component<EntityStore> {

    public static final float MAX_VALUE = 100.0f;
    public static final float MIN_VALUE = 0.0f;

    /** Les valeurs par défaut viennent de l'instance vierge (NeedsComponent::new) quand une clé est absente. */
    public static final BuilderCodec<NeedsComponent> CODEC =
        BuilderCodec.builder(NeedsComponent.class, NeedsComponent::new)
            .append(new KeyedCodec<>("Hunger", Codec.FLOAT),
                    NeedsComponent::setHunger, NeedsComponent::getHunger)
            .add()
            .append(new KeyedCodec<>("Fatigue", Codec.FLOAT),
                    NeedsComponent::setFatigue, NeedsComponent::getFatigue)
            .add()
            .append(new KeyedCodec<>("LastUpdate", Codec.INSTANT),
                    NeedsComponent::setLastUpdate, NeedsComponent::getLastUpdate)
            .add()
            .build();

    /** Satiété : 100 = rassasié, 0 = affamé. */
    private float hunger = MAX_VALUE;
    /** Fatigue : 0 = reposé, 100 = épuisé. */
    private float fatigue = MIN_VALUE;
    /** Heure de jeu de la dernière mise à jour ; null tant que le composant n'a pas été initialisé. */
    @Nullable
    private Instant lastUpdate;

    public NeedsComponent() {
    }

    public NeedsComponent(float hunger, float fatigue, @Nullable Instant lastUpdate) {
        this.hunger = hunger;
        this.fatigue = fatigue;
        this.lastUpdate = lastUpdate;
    }

    public static ComponentType<EntityStore, NeedsComponent> getComponentType() {
        return LivingWorldPlugin.get().getNeedsComponentType();
    }

    public float getHunger() {
        return hunger;
    }

    public void setHunger(float hunger) {
        this.hunger = clamp(hunger);
    }

    public float getFatigue() {
        return fatigue;
    }

    public void setFatigue(float fatigue) {
        this.fatigue = clamp(fatigue);
    }

    @Nullable
    public Instant getLastUpdate() {
        return lastUpdate;
    }

    public void setLastUpdate(@Nullable Instant lastUpdate) {
        this.lastUpdate = lastUpdate;
    }

    /** Vrai si la satiété est passée sous le seuil donné. */
    public boolean isHungry(float threshold) {
        return hunger < threshold;
    }

    /** Vrai si la fatigue a dépassé le seuil donné. */
    public boolean isTired(float threshold) {
        return fatigue > threshold;
    }

    @Override
    public Component<EntityStore> clone() {
        return new NeedsComponent(hunger, fatigue, lastUpdate);
    }

    private static float clamp(float value) {
        return Math.clamp(value, MIN_VALUE, MAX_VALUE);
    }
}
