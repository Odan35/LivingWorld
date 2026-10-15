package com.odan.livingworld.command;

import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;

import javax.annotation.Nonnull;

/**
 * Commande /livingworld : affiche le nom et la version du plugin dans le chat.
 */
public class LivingWorldCommand extends CommandBase {

    private final String pluginName;
    private final String pluginVersion;

    public LivingWorldCommand(String pluginName, String pluginVersion) {
        super("livingworld", "Affiche la version du plugin " + pluginName + ".");
        this.setPermissionGroup(GameMode.Adventure); // Utilisable par tous les joueurs, pas seulement les OP
        this.pluginName = pluginName;
        this.pluginVersion = pluginVersion;
    }

    @Override
    protected void executeSync(@Nonnull CommandContext ctx) {
        ctx.sendMessage(Message.raw(pluginName + " v" + pluginVersion));
    }
}
