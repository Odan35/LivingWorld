package com.odan.livingworld.needs;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Construit un identifiant lisible d'entité pour les logs.
 * À n'appeler qu'au moment de logger : la recherche du composant n'est pas gratuite.
 */
public final class EntityLabels {

    private EntityLabels() {
    }

    @Nonnull
    public static String of(@Nonnull ArchetypeChunk<EntityStore> chunk, int index) {
        UUIDComponent uuid = chunk.getComponent(index, UUIDComponent.getComponentType());
        return format(uuid, "ref#" + chunk.getReferenceTo(index).getIndex());
    }

    @Nonnull
    public static String of(@Nonnull Holder<EntityStore> holder) {
        // Le holder n'est pas encore dans le store : pas d'index de référence disponible
        return format(holder.getComponent(UUIDComponent.getComponentType()), "sans UUID");
    }

    /** Variante pour les composants NPC et les systèmes qui ne manipulent qu'une référence ; inclut le rôle. */
    @Nonnull
    public static String of(@Nonnull Ref<EntityStore> ref, @Nonnull ComponentAccessor<EntityStore> accessor) {
        NPCEntity npc = accessor.getComponent(ref, NPCEntity.getComponentType());
        UUIDComponent uuid = accessor.getComponent(ref, UUIDComponent.getComponentType());
        String id = uuid != null ? uuid.getUuid().toString() : "ref#" + ref.getIndex();
        return npc != null ? "PNJ " + npc.getRoleName() + " " + id : "PNJ " + id;
    }

    @Nonnull
    private static String format(@Nullable UUIDComponent uuid, @Nonnull String fallback) {
        return uuid != null ? "PNJ " + uuid.getUuid() : "PNJ (" + fallback + ")";
    }
}
