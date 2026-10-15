package com.odan.livingworld.debug;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.odan.livingworld.behavior.ActivityDescriber;
import com.odan.livingworld.needs.NeedsComponent;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Affiche au-dessus de chaque PNJ ayant des besoins une étiquette « rôle · satiété · activité »,
 * mise à jour une fois par seconde, dans les mondes où /livingworld labels l'a activée.
 *
 * Le nameplate est visible par tous les joueurs (c'est un composant de l'entité) : outil de
 * debug, pas d'affichage par joueur. Le nameplate d'origine est restauré à la désactivation.
 */
public class NpcLabelSystem extends EntityTickingSystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** Préfixe des étiquettes : permet de les reconnaître si une sauvegarde en a conservé une. */
    public static final String LABEL_PREFIX = "[LW] ";
    private static final float UPDATE_INTERVAL = 1.0f;

    private final ComponentType<EntityStore, NeedsComponent> needsType;
    private final ComponentType<EntityStore, NpcLabel> labelType;
    /** Noms des mondes où l'affichage est actif. Lu par les threads de tous les mondes. */
    private final Set<String> enabledWorlds = ConcurrentHashMap.newKeySet();

    public NpcLabelSystem(@Nonnull ComponentType<EntityStore, NeedsComponent> needsType,
                          @Nonnull ComponentType<EntityStore, NpcLabel> labelType) {
        this.needsType = needsType;
        this.labelType = labelType;
    }

    public boolean isEnabled(@Nonnull String worldName) {
        return enabledWorlds.contains(worldName);
    }

    public void enable(@Nonnull String worldName) {
        enabledWorlds.add(worldName);
        LOGGER.atInfo().log("NpcLabelSystem : étiquettes activées dans le monde %s", worldName);
    }

    /**
     * Désactive l'affichage et restaure les nameplates. À appeler sur le thread du monde,
     * hors traitement des systèmes (depuis une commande AbstractWorldCommand).
     * @return nombre de PNJ restaurés
     */
    public int disable(@Nonnull String worldName, @Nonnull Store<EntityStore> store) {
        enabledWorlds.remove(worldName);
        // On collecte d'abord : retirer un composant pendant l'itération modifierait les chunks parcourus
        List<Ref<EntityStore>> labelled = new ArrayList<>();
        store.forEachChunk(labelType, (chunk, buffer) -> {
            for (int i = 0; i < chunk.size(); i++) {
                labelled.add(chunk.getReferenceTo(i));
            }
        });
        for (Ref<EntityStore> ref : labelled) {
            if (ref.isValid()) {
                restore(ref, store);
            }
        }
        LOGGER.atInfo().log("NpcLabelSystem : étiquettes désactivées dans le monde %s (%d PNJ restaurés)",
            worldName, labelled.size());
        return labelled.size();
    }

    @Override
    public void tick(float deltaTime, int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
                     @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> buffer) {
        if (!isEnabled(store.getExternalData().getWorld().getName())) {
            return;
        }
        Ref<EntityStore> ref = chunk.getReferenceTo(index);
        NpcLabel label = chunk.getComponent(index, labelType);
        Nameplate nameplate = chunk.getComponent(index, Nameplate.getComponentType());

        if (label == null) {
            // Première fois pour ce PNJ (ou PNJ apparu après l'activation) : on mémorise son nameplate
            String original = nameplate != null ? nameplate.getText() : null;
            buffer.addComponent(ref, labelType, new NpcLabel(original, nameplate != null));
            String text = labelText(ref, store);
            if (nameplate != null) {
                nameplate.setText(text);
            } else {
                buffer.addComponent(ref, Nameplate.getComponentType(), new Nameplate(text));
            }
            return;
        }
        if (nameplate != null && label.advance(deltaTime, UPDATE_INTERVAL)) {
            nameplate.setText(labelText(ref, store));
        }
    }

    @Nonnull
    private static String labelText(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        ActivityDescriber.Activity activity = ActivityDescriber.describe(ref, store);
        NeedsComponent needs = store.getComponent(ref, NeedsComponent.getComponentType());
        String hunger = needs != null ? String.format(Locale.ROOT, "%.0f", needs.getHunger()) : "?";
        return LABEL_PREFIX + "satiété " + hunger + " · " + activity.shortLabel();
    }

    /** Remet le nameplate d'origine (ou le retire) et enlève le marqueur. */
    private void restore(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        NpcLabel label = store.getComponent(ref, labelType);
        if (label == null) {
            return;
        }
        if (label.hadNameplate()) {
            Nameplate nameplate = store.getComponent(ref, Nameplate.getComponentType());
            if (nameplate != null) {
                nameplate.setText(label.getOriginalText());
            }
        } else {
            store.tryRemoveComponent(ref, Nameplate.getComponentType());
        }
        store.removeComponent(ref, labelType);
    }

    @Override
    public Query<EntityStore> getQuery() {
        return needsType;
    }
}
