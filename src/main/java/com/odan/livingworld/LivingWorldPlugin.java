package com.odan.livingworld;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.odan.livingworld.command.LivingWorldCommand;
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

        this.getEntityStoreRegistry().registerSystem(new NeedsAttachSystem(needsComponentType));
        this.getEntityStoreRegistry().registerSystem(
            new NeedsTickSystem(needsComponentType, HUNGER_PER_GAME_HOUR, FATIGUE_PER_GAME_HOUR));

        this.getCommandRegistry().registerCommand(new LivingWorldCommand(this.getName(), this.getManifest().getVersion().toString()));
    }
}
