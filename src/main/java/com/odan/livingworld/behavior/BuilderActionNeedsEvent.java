package com.odan.livingworld.behavior;

import com.google.gson.JsonElement;
import com.hypixel.hytale.server.npc.asset.builder.Builder;
import com.hypixel.hytale.server.npc.asset.builder.BuilderDescriptorState;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.builders.BuilderActionBase;
import com.hypixel.hytale.server.npc.instructions.Action;

import javax.annotation.Nonnull;
import java.util.function.Supplier;

/**
 * Lit la configuration JSON de l'action « LivingWorld_NeedsEvent » :
 * <pre>{ "Type": "LivingWorld_NeedsEvent", "Event": "StartForaging" }</pre>
 */
public class BuilderActionNeedsEvent extends BuilderActionBase {

    public static final String TYPE = "LivingWorld_NeedsEvent";

    /** Transitions de comportement loguées par l'action. */
    public enum Event implements Supplier<String> {
        StartForaging("commence à chercher de la nourriture"),
        StartHunting("repère une proie et l'attaque"),
        GiveUp("abandonne : rien trouvé à manger, réessaiera bientôt"),
        WakeHungryHerbivore("se réveille affamé pour aller brouter"),
        FleePredator("repère un prédateur et s'enfuit"),
        ForageInterrupted("renonce à manger : un prédateur rôde ou il vient de fuir"),
        SmellMeat("repère de la viande au sol et va la manger"),
        LostMeat("ne trouve plus la viande"),
        StartProwling("part en patrouille pour trouver une proie"),
        TrackPrey("flaire une proie et remonte sa piste"),
        LostTrack("perd la piste et reprend la patrouille"),
        StopProwling("est rassasié et arrête de chasser"),
        WakeHungry("se réveille affamé pour chasser");

        private final String message;

        Event(String message) {
            this.message = message;
        }

        @Override
        public String get() {
            return message;
        }
    }

    protected Event event = Event.StartForaging;

    @Override
    public Action build(BuilderSupport support) {
        return new ActionNeedsEvent(this);
    }

    @Override
    public String getShortDescription() {
        return "Logue une transition de comportement lié aux besoins (LivingWorld)";
    }

    @Override
    public String getLongDescription() {
        return "N'a aucun effet en jeu : écrit un log INFO avec le PNJ, l'événement et sa satiété.";
    }

    @Override
    public BuilderDescriptorState getBuilderDescriptorState() {
        return BuilderDescriptorState.Stable;
    }

    @Override
    public Builder<Action> readConfig(JsonElement data) {
        this.requireEnum(data, "Event", value -> this.event = value, Event.class,
            BuilderDescriptorState.Stable, "Événement à loguer", null);
        return this;
    }

    @Nonnull
    public Event getEvent() {
        return event;
    }
}
