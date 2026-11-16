package com.hfstudio.bqapi.runtime.apply;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import net.minecraft.nbt.NBTTagCompound;

import com.hfstudio.bqapi.api.definition.ItemRewardDefinition;
import com.hfstudio.bqapi.api.definition.RawRewardDefinition;
import com.hfstudio.bqapi.api.definition.RewardDefinition;

import betterquesting.api.questing.rewards.IReward;
import betterquesting.api.utils.BigItemStack;
import betterquesting.api.utils.JsonHelper;
import betterquesting.questing.rewards.RewardRegistry;
import bq_standard.rewards.RewardItem;

public class RewardApplier {

    public Map<Integer, IReward> buildRewards(Iterable<RewardDefinition> definitions) {
        Map<Integer, IReward> rewards = new LinkedHashMap<>();
        DefinitionSlots.assign(definitions, RewardDefinition::getSlot, "reward")
            .forEach(
                (slot, definition) -> rewards.put(slot, Objects.requireNonNull(createReward(definition), "reward")));

        return rewards;
    }

    protected IReward createReward(RewardDefinition definition) {
        if (definition instanceof ItemRewardDefinition itemReward) {
            RewardItem reward = new RewardItem();
            for (NBTTagCompound itemNbt : itemReward.getItems()) {
                BigItemStack stack = JsonHelper.JsonToItemStack(itemNbt);
                if (stack == null) {
                    throw new IllegalArgumentException("Unable to load reward item from NBT");
                }
                reward.items.add(stack);
            }
            return reward;
        }
        if (definition instanceof RawRewardDefinition raw) {
            IReward reward = RewardRegistry.INSTANCE.createNew(raw.getFactoryId());
            if (reward == null) {
                throw new IllegalArgumentException("Unknown BetterQuesting reward factory: " + raw.getFactoryId());
            }
            reward.readFromNBT(raw.getConfigNbt());
            return reward;
        }

        throw new IllegalArgumentException(
            "Unsupported reward definition: " + definition.getClass()
                .getName());
    }
}
