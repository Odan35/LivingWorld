package com.odan.livingworld;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.odan.livingworld.behavior.BuilderActionEat;
import com.odan.livingworld.behavior.BuilderActionNeedsEvent;
import com.odan.livingworld.behavior.BuilderSensorHungry;
import com.odan.livingworld.behavior.BuilderSensorDiet;
import com.odan.livingworld.behavior.PredatorKillSystem;
import com.odan.livingworld.command.LivingWorldCommand;
import com.odan.livingworld.debug.NpcLabel;
import com.odan.livingworld.debug.NpcLabelSystem;
import com.odan.livingworld.needs.NeedsAttachSystem;
import com.odan.livingworld.needs.NeedsComponent;
import com.odan.livingworld.needs.NeedsTickSystem;

import javax.annotation.Nonnull;

/**
 * Point d'entrée du plugin LivingWorld. La méthode setup sert à s'enregistrer
 * dans les registres du jeu (commandes, composants, systèmes, événements).
 */
public class LivingWorldPlugin extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** Identifiant de sauvegarde du composant : ne plus le changer une fois des mondes sauvegardés. */
    private static final String NEEDS_COMPONENT_ID = "LivingWorldNeeds";
    private static final float HUNGER_PER_GAME_HOUR = 4.0f;
    private static final float FATIGUE_PER_GAME_HOUR = 3.0f;

    private static LivingWorldPlugin instance;

    private ComponentType<EntityStore, NeedsComponent> needsComponentType;

    public LivingWorldPlugin(@Nonnull JavaPluginInit init) {
        super(init);
        instance = this;
    }

    public static LivingWorldPlugin get() {
        return instance;
    }

    public ComponentType<EntityStore, NeedsComponent> getNeedsComponentType() {
        return needsComponentType;
    }

    @Override
    protected void setup() {
        LOGGER.atInfo().log("Initialisation de " + this.getName() + " " + this.getManifest().getVersion());

        // Enregistrement avec un nom et un codec : le composant est persisté avec l'entité
        needsComponentType = this.getEntityStoreRegistry()
            .registerComponent(NeedsComponent.class, NEEDS_COMPONENT_ID, NeedsComponent.CODEC);
        LOGGER.atInfo().log("LivingWorldPlugin : composant %s enregistré", NEEDS_COMPONENT_ID);

        NeedsTickSystem tickSystem =
            new NeedsTickSystem(needsComponentType, HUNGER_PER_GAME_HOUR, FATIGUE_PER_GAME_HOUR);
        this.getEntityStoreRegistry().registerSystem(new NeedsAttachSystem(needsComponentType));
        this.getEntityStoreRegistry().registerSystem(tickSystem);
        LOGGER.atInfo().log("LivingWorldPlugin : systèmes enregistrés (faim -%.1f/h, fatigue +%.1f/h de jeu)",
            HUNGER_PER_GAME_HOUR, FATIGUE_PER_GAME_HOUR);

        // Composants NPC custom, utilisables dans les fichiers Role JSON. Enregistrés dans setup,
        // donc avant le chargement des rôles qui les référencent (templates herbivore, prédateur, araignée)
        NPCPlugin.get()
            .registerCoreComponentType(BuilderSensorHungry.TYPE, BuilderSensorHungry::new)
            .registerCoreComponentType(BuilderSensorDiet.TYPE, BuilderSensorDiet::new)
            .registerCoreComponentType(BuilderActionNeedsEvent.TYPE, BuilderActionNeedsEvent::new)
            .registerCoreComponentType(BuilderActionEat.TYPE, BuilderActionEat::new);
        this.getEntityStoreRegistry().registerSystem(new PredatorKillSystem());
        LOGGER.atInfo().log("LivingWorldPlugin : composants NPC %s, %s, %s, %s et suivi des mises à mort enregistrés",
            BuilderSensorHungry.TYPE, BuilderSensorDiet.TYPE, BuilderActionNeedsEvent.TYPE, BuilderActionEat.TYPE);

        // Étiquettes de debug au-dessus des PNJ : marqueur non persistant (pas de codec)
        ComponentType<EntityStore, NpcLabel> labelType =
            this.getEntityStoreRegistry().registerComponent(NpcLabel.class, NpcLabel::new);
        NpcLabelSystem labelSystem = new NpcLabelSystem(needsComponentType, labelType);
        this.getEntityStoreRegistry().registerSystem(labelSystem);

        this.getCommandRegistry().registerCommand(new LivingWorldCommand(
            this.getName(), this.getManifest().getVersion().toString(), needsComponentType, tickSystem, labelSystem));
        LOGGER.atInfo().log("LivingWorldPlugin : commande /livingworld enregistrée (status, debug, starve, inspect, labels)");
    }
}
