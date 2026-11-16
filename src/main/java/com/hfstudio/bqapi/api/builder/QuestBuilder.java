package com.hfstudio.bqapi.api.builder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.hfstudio.bqapi.api.definition.QuestDefinition;
import com.hfstudio.bqapi.api.definition.RewardDefinition;
import com.hfstudio.bqapi.api.definition.TaskDefinition;
import com.hfstudio.bqapi.runtime.BQStableIdFactory;

import betterquesting.api.questing.IQuest;
import betterquesting.api.utils.BigItemStack;

public class QuestBuilder {

    private final String id;
    private UUID uuid;
    private final List<TaskDefinition> tasks = new ArrayList<>();
    private final List<UUID> prerequisites = new ArrayList<>();
    private final Map<UUID, IQuest.RequirementType> requirementTypes = new LinkedHashMap<>();
    private final List<RewardDefinition> rewards = new ArrayList<>();
    private NBTTagCompound iconNbt;
    private NBTTagCompound templateNbt;
    private boolean main;

    protected QuestBuilder(String id) {
        this.id = Objects.requireNonNull(id, "id");
    }

    public static QuestBuilder quest(String id) {
        return new QuestBuilder(id);
    }

    /**
     * Preserves every field, including the UUID. Added tasks and rewards append to the existing lists.
     */
    public static QuestBuilder from(QuestDefinition definition) {
        QuestBuilder b = new QuestBuilder(definition.getId());
        b.uuid = definition.getUuid();
        b.tasks.addAll(definition.getTasks());
        b.prerequisites.addAll(definition.getPrerequisites());
        b.requirementTypes.putAll(definition.getRequirementTypes());
        b.rewards.addAll(definition.getRewards());
        b.iconNbt = definition.getIconNbt();
        b.templateNbt = definition.getTemplateNbt();
        b.main = definition.isMain();
        return b;
    }

    public QuestBuilder task(TaskDefinition task) {
        this.tasks.add(Objects.requireNonNull(task, "task"));
        return this;
    }

    public QuestBuilder reward(RewardDefinition reward) {
        this.rewards.add(Objects.requireNonNull(reward, "reward"));
        return this;
    }

    public QuestBuilder prerequisite(QuestDefinition quest) {
        return prerequisite(
            Objects.requireNonNull(quest, "quest")
                .getUuid());
    }

    public QuestBuilder prerequisite(UUID prerequisiteUuid) {
        return prerequisite(prerequisiteUuid, IQuest.RequirementType.NORMAL);
    }

    public QuestBuilder prerequisite(UUID prerequisiteUuid, IQuest.RequirementType requirementType) {
        UUID uuid = Objects.requireNonNull(prerequisiteUuid, "prerequisiteUuid");
        if (!this.prerequisites.contains(uuid)) {
            this.prerequisites.add(uuid);
        }
        this.requirementTypes.put(uuid, Objects.requireNonNull(requirementType, "requirementType"));
        return this;
    }

    public QuestBuilder uuid(UUID uuid) {
        this.uuid = Objects.requireNonNull(uuid, "uuid");
        return this;
    }

    public QuestBuilder icon(ItemStack icon) {
        this.iconNbt = Objects.requireNonNull(icon, "icon")
            .writeToNBT(new NBTTagCompound());
        return this;
    }

    public QuestBuilder icon(String itemId, int count, int damage) {
        this.iconNbt = SerializedItemNbt.of(Objects.requireNonNull(itemId, "itemId"), count, damage);
        return this;
    }

    public QuestBuilder icon(BigItemStack icon) {
        this.iconNbt = Objects.requireNonNull(icon, "icon")
            .writeToNBT(new NBTTagCompound());
        return this;
    }

    public QuestBuilder iconNbt(NBTTagCompound iconNbt) {
        this.iconNbt = iconNbt == null ? null : (NBTTagCompound) iconNbt.copy();
        return this;
    }

    public QuestBuilder templateNbt(NBTTagCompound templateNbt) {
        this.templateNbt = templateNbt == null ? null : (NBTTagCompound) templateNbt.copy();
        return this;
    }

    public QuestBuilder main(boolean main) {
        this.main = main;
        return this;
    }

    public QuestDefinition build() {
        return new QuestDefinition(
            id,
            uuid == null ? BQStableIdFactory.quest(id) : uuid,
            tasks,
            prerequisites,
            requirementTypes,
            rewards,
            iconNbt,
            templateNbt,
            main);
    }
}
