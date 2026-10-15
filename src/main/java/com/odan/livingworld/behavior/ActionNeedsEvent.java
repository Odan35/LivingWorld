package com.odan.livingworld.behavior;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.corecomponents.ActionBase;
import com.hypixel.hytale.server.npc.instructions.ExecutionSupport;
import com.hypixel.hytale.server.npc.sensorinfo.InfoProvider;
import com.odan.livingworld.needs.EntityLabels;
import com.odan.livingworld.needs.NeedsComponent;

import javax.annotation.Nonnull;
import java.util.Locale;

/**
 * Action « LivingWorld_NeedsEvent » : log INFO d'une transition (début de recherche, de chasse,
 * abandon). Placée dans les rôles uniquement sur les changements de sous-état, jamais dans
 * une instruction évaluée à chaque tick.
 */
public class ActionNeedsEvent extends ActionBase {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final BuilderActionNeedsEvent.Event event;

    public ActionNeedsEvent(@Nonnull BuilderActionNeedsEvent builder) {
        super(builder);
        this.event = builder.getEvent();
    }

    @Override
    public boolean execute(@Nonnull Ref<EntityStore> ref, @Nonnull ExecutionSupport support, InfoProvider sensorInfo,
                           double dt, @Nonnull Store<EntityStore> store) {
        super.execute(ref, support, sensorInfo, dt, store);
        NeedsComponent needs = store.getComponent(ref, NeedsComponent.getComponentType());
        String hunger = needs != null ? String.format(Locale.ROOT, "%.1f", needs.getHunger()) : "?";
        LOGGER.atInfo().log("NeedsBehavior : %s %s (satiété %s)", EntityLabels.of(ref, store), event.get(), hunger);
        return true;
    }
}
