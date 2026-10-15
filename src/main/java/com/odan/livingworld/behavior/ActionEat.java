package com.odan.livingworld.behavior;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.MathUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.modules.blockset.BlockSetModule;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.ActionBase;
import com.hypixel.hytale.server.npc.instructions.ExecutionSupport;
import com.hypixel.hytale.server.npc.sensorinfo.IPositionProvider;
import com.hypixel.hytale.server.npc.sensorinfo.InfoProvider;
import com.odan.livingworld.needs.EntityLabels;
import com.odan.livingworld.needs.NeedsComponent;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Action « LivingWorld_Eat » : fin de repas d'un herbivore. Augmente la satiété et, si demandé,
 * retire le bloc ciblé par le sensor Block de l'instruction (même mécanique que l'action
 * vanilla PlaceBlock, avec le bloc vide). S'exécute sur le thread du monde, comme tout le rôle.
 */
// BlockSetModule est déprécié en 0.7.2 mais reste celui qu'utilise le sensor Block vanilla (pas de remplaçant)
@SuppressWarnings("removal")
public class ActionEat extends ActionBase {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final float amount;
    private final boolean consumeBlock;
    private final int consumableBlockSet;

    public ActionEat(@Nonnull BuilderActionEat builder, @Nonnull BuilderSupport support) {
        super(builder);
        this.amount = builder.getAmount(support);
        this.consumeBlock = builder.isConsumeBlock(support);
        this.consumableBlockSet = builder.getConsumableBlockSet(support);
        if (consumeBlock && consumableBlockSet == BuilderActionEat.NO_BLOCK_SET) {
            // Erreur de configuration du rôle : le repas fonctionnera, mais sans retirer de bloc
            LOGGER.atWarning().log("ActionEat : BlockSet « %s » introuvable, aucun bloc ne sera consommé",
                builder.getConsumableBlockSetName(support));
        }
    }

    @Override
    public boolean execute(@Nonnull Ref<EntityStore> ref, @Nonnull ExecutionSupport support, InfoProvider sensorInfo,
                           double dt, @Nonnull Store<EntityStore> store) {
        super.execute(ref, support, sensorInfo, dt, store);
        NeedsComponent needs = store.getComponent(ref, NeedsComponent.getComponentType());
        if (needs == null) {
            return true;
        }

        float before = needs.getHunger();
        needs.setHunger(before + amount);
        String eaten = consumeBlock ? consumeTargetBlock(sensorInfo, store) : null;
        String sensorPos = describeSensorPosition(sensorInfo);

        LOGGER.atInfo().log(String.format(Locale.ROOT, "NeedsBehavior : %s a fini de manger (satiété %.1f -> %.1f)%s%s",
            EntityLabels.of(ref, store), before, needs.getHunger(),
            eaten != null ? ", " + eaten : "",
            // Position de la viande (ou autre cible) pour les prédateurs : utile pour debug la chaîne scavenge.
            // Si ConsumeBlock=true, la position du bloc est déjà incluse dans « eaten ».
            (eaten == null && sensorPos != null) ? ", cible en " + sensorPos : ""));
        return true;
    }

    /**
     * Position (x, y, z) du sensor de l'instruction, si celui-ci en expose une (Block, DroppedItem…).
     * @return "x.x, y.y, z.z" ou null si non disponible
     */
    @Nullable
    private static String describeSensorPosition(@Nullable InfoProvider sensorInfo) {
        if (sensorInfo == null || !sensorInfo.hasPosition()) {
            return null;
        }
        IPositionProvider p = sensorInfo.getPositionProvider();
        if (p == null) {
            return null;
        }
        return String.format(Locale.ROOT, "(%.1f, %.1f, %.1f)", p.getX(), p.getY(), p.getZ());
    }

    /**
     * Retire le bloc visé par le sensor s'il fait partie du BlockSet consommable.
     * @return description du bloc consommé pour le log, ou null si rien n'a été retiré
     */
    @Nullable
    private String consumeTargetBlock(@Nullable InfoProvider sensorInfo, @Nonnull Store<EntityStore> store) {
        if (consumableBlockSet == BuilderActionEat.NO_BLOCK_SET) {
            return null;
        }
        IPositionProvider position = sensorInfo != null && sensorInfo.hasPosition() ? sensorInfo.getPositionProvider() : null;
        if (position == null) {
            // Le sensor n'a plus de cible (plante déjà mangée par un congénère, par exemple)
            LOGGER.atWarning().atMostEvery(30, TimeUnit.SECONDS).log("ActionEat : pas de position de bloc, rien n'est consommé");
            return null;
        }
        int x = MathUtil.floor(position.getX());
        int y = MathUtil.floor(position.getY());
        int z = MathUtil.floor(position.getZ());

        ChunkStore chunkStore = store.getExternalData().getWorld().getChunkStore();
        Ref<ChunkStore> section = chunkStore.getChunkSectionReferenceAtBlock(x, y, z);
        if (section == null || !section.isValid()) {
            return null;
        }
        BlockSection blocks = chunkStore.getStore().getComponent(section, BlockSection.getComponentType());
        if (blocks == null) {
            return null;
        }
        int blockId = blocks.get(x, y, z);
        if (!BlockSetModule.getInstance().blockInSet(consumableBlockSet, blockId)) {
            // Foin, sol… : on mange sans détruire
            return null;
        }
        BlockType eaten = BlockType.getAssetMap().getAsset(blockId);
        BlockOperations.setBlock(chunkStore, section, x, y, z, BlockType.EMPTY_ID, BlockType.EMPTY, 0, 0, 0);
        return String.format(Locale.ROOT, "bloc %s consommé en (%d, %d, %d)",
            eaten != null ? eaten.getId() : "#" + blockId, x, y, z);
    }
}
