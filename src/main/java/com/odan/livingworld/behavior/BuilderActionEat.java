package com.odan.livingworld.behavior;

import com.google.gson.JsonElement;
import com.hypixel.hytale.server.core.asset.type.blockset.config.BlockSet;
import com.hypixel.hytale.server.npc.asset.builder.Builder;
import com.hypixel.hytale.server.npc.asset.builder.BuilderDescriptorState;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.asset.builder.holder.BooleanHolder;
import com.hypixel.hytale.server.npc.asset.builder.holder.DoubleHolder;
import com.hypixel.hytale.server.npc.asset.builder.holder.StringHolder;
import com.hypixel.hytale.server.npc.asset.builder.validators.DoubleRangeValidator;
import com.hypixel.hytale.server.npc.corecomponents.builders.BuilderActionBase;
import com.hypixel.hytale.server.npc.instructions.Action;
import com.odan.livingworld.needs.NeedsComponent;

import javax.annotation.Nonnull;

/**
 * Lit la configuration JSON de l'action « LivingWorld_Eat » :
 * <pre>{ "Type": "LivingWorld_Eat", "Amount": 35, "ConsumeBlock": true,
 *   "ConsumableBlockSet": "LivingWorld_ConsumablePlants" }</pre>
 */
// BlockSet est déprécié en 0.7.2 mais reste celui qu'utilise le sensor Block vanilla (pas de remplaçant)
@SuppressWarnings("removal")
public class BuilderActionEat extends BuilderActionBase {

    public static final String TYPE = "LivingWorld_Eat";
    /** Index renvoyé quand aucun BlockSet n'est configuré ou qu'il est introuvable. */
    public static final int NO_BLOCK_SET = Integer.MIN_VALUE;

    protected final DoubleHolder amount = new DoubleHolder();
    protected final BooleanHolder consumeBlock = new BooleanHolder();
    protected final StringHolder consumableBlockSet = new StringHolder();

    @Override
    public Action build(BuilderSupport support) {
        return new ActionEat(this, support);
    }

    @Override
    public String getShortDescription() {
        return "Le PNJ mange : satiété augmentée, bloc éventuellement consommé (LivingWorld)";
    }

    @Override
    public String getLongDescription() {
        return "Ajoute Amount à la satiété du NeedsComponent. Si ConsumeBlock est vrai, le bloc ciblé par "
            + "le sensor de l'instruction est retiré s'il appartient à ConsumableBlockSet.";
    }

    @Override
    public BuilderDescriptorState getBuilderDescriptorState() {
        return BuilderDescriptorState.Stable;
    }

    @Override
    public Builder<Action> readConfig(JsonElement data) {
        this.getDouble(data, "Amount", this.amount, 35.0,
            DoubleRangeValidator.between(NeedsComponent.MIN_VALUE, NeedsComponent.MAX_VALUE),
            BuilderDescriptorState.Stable, "Satiété regagnée", null);
        this.getBoolean(data, "ConsumeBlock", this.consumeBlock, false,
            BuilderDescriptorState.Stable, "Retirer le bloc mangé", null);
        this.getString(data, "ConsumableBlockSet", this.consumableBlockSet, "", null,
            BuilderDescriptorState.Stable, "BlockSet des blocs qui disparaissent une fois mangés", null);
        return this;
    }

    public float getAmount(@Nonnull BuilderSupport support) {
        return (float) amount.get(support.getExecutionContext());
    }

    public boolean isConsumeBlock(@Nonnull BuilderSupport support) {
        return consumeBlock.get(support.getExecutionContext());
    }

    /** Index du BlockSet configuré, ou {@link #NO_BLOCK_SET}. */
    public int getConsumableBlockSet(@Nonnull BuilderSupport support) {
        String name = consumableBlockSet.get(support.getExecutionContext());
        if (name == null || name.isEmpty()) {
            return NO_BLOCK_SET;
        }
        return BlockSet.getAssetMap().getIndexOrDefault(name, NO_BLOCK_SET);
    }

    @Nonnull
    public String getConsumableBlockSetName(@Nonnull BuilderSupport support) {
        String name = consumableBlockSet.get(support.getExecutionContext());
        return name != null ? name : "";
    }
}
