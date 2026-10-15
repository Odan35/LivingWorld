package com.odan.livingworld.command;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.arguments.types.EntityWrappedArg;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractWorldCommand;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.permissions.provider.HytalePermissionsProvider;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.odan.livingworld.behavior.ActivityDescriber;
import com.odan.livingworld.debug.NpcLabelSystem;
import com.odan.livingworld.needs.EntityLabels;
import com.odan.livingworld.needs.NeedsComponent;
import com.odan.livingworld.needs.NeedsTickSystem;

import javax.annotation.Nonnull;
import java.time.Instant;
import java.util.Locale;

/**
 * Commande /livingworld : affiche le nom et la version du plugin dans le chat.
 * Sous-commandes :
 * - status : statistiques des besoins des PNJ chargés dans le monde ;
 * - debug  : force une mise à jour des besoins de tous les PNJ chargés ;
 * - starve : affame les PNJ chargés (test) ;
 * - inspect : explique ce que fait le PNJ regardé ;
 * - labels : affiche/masque l'activité des PNJ au-dessus de leur tête.
 */
public class LivingWorldCommand extends CommandBase {

    private final String pluginName;
    private final String pluginVersion;

    public LivingWorldCommand(String pluginName, String pluginVersion,
                              @Nonnull ComponentType<EntityStore, NeedsComponent> needsType,
                              @Nonnull NeedsTickSystem tickSystem,
                              @Nonnull NpcLabelSystem labelSystem) {
        super("livingworld", "Affiche la version du plugin " + pluginName + ".");
        this.setPermissionGroups(HytalePermissionsProvider.GROUP_ADVENTURER); // Utilisable par tous les joueurs, pas seulement les OP
        this.pluginName = pluginName;
        this.pluginVersion = pluginVersion;

        this.addSubCommand(new StatusCommand(needsType));
        this.addSubCommand(new DebugCommand(needsType, tickSystem));
        this.addSubCommand(new StarveCommand(needsType));
        this.addSubCommand(new InspectCommand());
        this.addSubCommand(new LabelsCommand(labelSystem));
    }

    @Override
    protected void executeSync(@Nonnull CommandContext ctx) {
        ctx.sendMessage(Message.raw(pluginName + " v" + pluginVersion));
    }

    /**
     * /livingworld status [--world]. Exécutée sur le thread du monde, donc lecture sûre du store.
     * Seuls les PNJ des chunks chargés sont présents dans le store : les autres ne sont pas comptés.
     */
    private static class StatusCommand extends AbstractWorldCommand {

        private final ComponentType<EntityStore, NeedsComponent> needsType;

        StatusCommand(@Nonnull ComponentType<EntityStore, NeedsComponent> needsType) {
            super("status", "Statistiques des besoins des PNJ chargés.");
            this.setPermissionGroups(HytalePermissionsProvider.GROUP_ADVENTURER); // Lecture seule : ouverte à tous
            this.needsType = needsType;
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull World world, @Nonnull Store<EntityStore> store) {
            // Tableau pour pouvoir cumuler depuis la lambda : [nombre, somme satiété, somme fatigue]
            double[] totals = new double[3];
            store.forEachChunk(needsType, (chunk, buffer) -> {
                for (int i = 0; i < chunk.size(); i++) {
                    NeedsComponent needs = chunk.getComponent(i, needsType);
                    totals[0]++;
                    totals[1] += needs.getHunger();
                    totals[2] += needs.getFatigue();
                }
            });

            int count = (int) totals[0];
            ctx.sendMessage(Message.raw("LivingWorld - monde " + world.getName()));
            ctx.sendMessage(Message.raw("PNJ avec NeedsComponent (chargés) : " + count));
            if (count > 0) {
                ctx.sendMessage(Message.raw(String.format(Locale.ROOT,
                    "Satiété moyenne : %.1f / 100 (100 = rassasié)", totals[1] / count)));
                ctx.sendMessage(Message.raw(String.format(Locale.ROOT,
                    "Fatigue moyenne : %.1f / 100 (100 = épuisé)", totals[2] / count)));
            }
            // Pas encore de simulation de meutes : on l'indique plutôt que d'afficher un faux zéro
            ctx.sendMessage(Message.raw("Meutes simulées hors champ : non implémenté"));
        }
    }

    /**
     * /livingworld debug [--world]. Réservée aux OP (pas de groupe de permission explicite).
     * Le calcul étant basé sur le temps de jeu écoulé, le tick forcé applique immédiatement
     * le temps écoulé depuis la dernière mise à jour de chaque PNJ.
     */
    private static class DebugCommand extends AbstractWorldCommand {

        private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

        private final ComponentType<EntityStore, NeedsComponent> needsType;
        private final NeedsTickSystem tickSystem;

        DebugCommand(@Nonnull ComponentType<EntityStore, NeedsComponent> needsType,
                     @Nonnull NeedsTickSystem tickSystem) {
            super("debug", "Force un tick de simulation des besoins sur tous les PNJ chargés.");
            this.needsType = needsType;
            this.tickSystem = tickSystem;
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull World world, @Nonnull Store<EntityStore> store) {
            Instant now = store.getResource(WorldTimeResource.getResourceType()).getGameTime();
            int[] count = new int[1];
            store.forEachChunk(needsType, (chunk, buffer) -> {
                for (int i = 0; i < chunk.size(); i++) {
                    tickSystem.simulate(chunk, i, now);
                    count[0]++;
                }
            });

            LOGGER.atInfo().log("LivingWorldCommand : tick forcé sur %d PNJ du monde %s", count[0], world.getName());
            ctx.sendMessage(Message.raw("Tick forcé sur " + count[0] + " PNJ du monde " + world.getName() + "."));
        }
    }

    /**
     * /livingworld starve [--world]. Outil de test réservé aux OP : passe la satiété de tous les
     * PNJ chargés sous le seuil de faim, pour observer les comportements sans attendre des heures de jeu.
     */
    private static class StarveCommand extends AbstractWorldCommand {

        private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
        private static final float STARVED_HUNGER = 20.0f;

        private final ComponentType<EntityStore, NeedsComponent> needsType;

        StarveCommand(@Nonnull ComponentType<EntityStore, NeedsComponent> needsType) {
            super("starve", "Affame tous les PNJ chargés (test des comportements de faim).");
            this.needsType = needsType;
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull World world, @Nonnull Store<EntityStore> store) {
            int[] count = new int[1];
            store.forEachChunk(needsType, (chunk, buffer) -> {
                for (int i = 0; i < chunk.size(); i++) {
                    chunk.getComponent(i, needsType).setHunger(STARVED_HUNGER);
                    count[0]++;
                }
            });

            LOGGER.atInfo().log("LivingWorldCommand : %d PNJ affamés (satiété %.0f) dans le monde %s",
                count[0], STARVED_HUNGER, world.getName());
            ctx.sendMessage(Message.raw(count[0] + " PNJ affamés dans le monde " + world.getName() + "."));
        }
    }

    /**
     * /livingworld inspect [--entity]. Explique dans le chat ce que fait le PNJ regardé
     * (ou celui donné par UUID) : besoins, régime, état du rôle et activité en clair.
     * Lecture seule : ouverte à tous.
     */
    private static class InspectCommand extends AbstractWorldCommand {

        private final EntityWrappedArg entityArg;

        InspectCommand() {
            super("inspect", "Explique ce que fait le PNJ que vous regardez.");
            this.setPermissionGroups(HytalePermissionsProvider.GROUP_ADVENTURER);
            this.entityArg = this.withOptionalArg("entity", "PNJ à inspecter (par défaut : celui regardé)", ArgTypes.ENTITY_ID);
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull World world, @Nonnull Store<EntityStore> store) {
            Ref<EntityStore> ref = entityArg.get(store, ctx);
            if (ref == null || !ref.isValid() || store.getComponent(ref, NPCEntity.getComponentType()) == null) {
                ctx.sendMessage(Message.raw("Aucun PNJ visé : regardez un PNJ ou passez --entity <uuid>."));
                return;
            }
            ActivityDescriber.Activity activity = ActivityDescriber.describe(ref, store);
            ctx.sendMessage(Message.raw("LivingWorld - " + EntityLabels.of(ref, store) + " : " + activity.shortLabel()));
            for (String line : activity.details()) {
                ctx.sendMessage(Message.raw("  " + line));
            }
        }
    }

    /**
     * /livingworld labels [--world]. Active ou désactive les étiquettes au-dessus des PNJ
     * (satiété et activité, mises à jour chaque seconde). Réservée aux OP : le texte est
     * visible par tous les joueurs du monde.
     */
    private static class LabelsCommand extends AbstractWorldCommand {

        private final NpcLabelSystem labelSystem;

        LabelsCommand(@Nonnull NpcLabelSystem labelSystem) {
            super("labels", "Affiche/masque l'activité des PNJ au-dessus de leur tête.");
            this.labelSystem = labelSystem;
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull World world, @Nonnull Store<EntityStore> store) {
            if (labelSystem.isEnabled(world.getName())) {
                int restored = labelSystem.disable(world.getName(), store);
                ctx.sendMessage(Message.raw("Étiquettes LivingWorld désactivées (" + restored + " PNJ restaurés)."));
            } else {
                labelSystem.enable(world.getName());
                ctx.sendMessage(Message.raw("Étiquettes LivingWorld activées dans " + world.getName()
                    + ". Relancez /livingworld labels pour les retirer."));
            }
        }
    }
}
