package com.hfstudio.bqapi.runtime.apply;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;

import com.hfstudio.bqapi.api.definition.QuestDefinition;

import betterquesting.api.properties.NativeProps;
import betterquesting.api.questing.IQuest;
import betterquesting.api.questing.rewards.IReward;
import betterquesting.api.questing.tasks.ITask;
import betterquesting.api.utils.BigItemStack;
import betterquesting.api.utils.JsonHelper;
import betterquesting.api2.storage.DBEntry;
import betterquesting.questing.QuestDatabase;
import betterquesting.questing.rewards.RewardStorage;
import betterquesting.questing.tasks.TaskStorage;

public class QuestApplier {

    private final TaskApplier taskApplier;
    private final RewardApplier rewardApplier;

    public QuestApplier(TaskApplier taskApplier) {
        this(taskApplier, new RewardApplier());
    }

    public QuestApplier(TaskApplier taskApplier, RewardApplier rewardApplier) {
        this.taskApplier = Objects.requireNonNull(taskApplier, "taskApplier");
        this.rewardApplier = Objects.requireNonNull(rewardApplier, "rewardApplier");
    }

    public void apply(QuestDefinition definition, boolean patched) {
        apply(prepare(definition, patched));
    }

    public PreparedQuest prepare(QuestDefinition definition, boolean patched) {
        Objects.requireNonNull(definition, "definition");
        NBTTagCompound template = definition.getTemplateNbt();
        boolean useDefinitionProperties = template == null || patched;
        Map<Integer, ITask> tasks;
        Map<Integer, IReward> rewards;
        if (useDefinitionProperties) {
            tasks = taskApplier.buildTasks(definition.getTasks());
            rewards = rewardApplier.buildRewards(definition.getRewards());
        } else {
            TaskStorage taskStorage = new TaskStorage();
            taskStorage.readFromNBT(template.getTagList("tasks", 10), false);
            RewardStorage rewardStorage = new RewardStorage();
            rewardStorage.readFromNBT(template.getTagList("rewards", 10), false);
            tasks = copyEntries(taskStorage.getEntries());
            rewards = copyEntries(rewardStorage.getEntries());
        }
        NBTTagCompound iconNbt = useDefinitionProperties ? definition.getIconNbt() : null;
        BigItemStack icon = iconNbt == null ? null : loadItem(iconNbt);
        return new PreparedQuest(definition, template, icon, tasks, rewards, useDefinitionProperties);
    }

    public void apply(PreparedQuest prepared) {
        QuestDefinition definition = prepared.definition();
        IQuest quest = QuestDatabase.INSTANCE.get(definition.getUuid());
        Map<Integer, ITask> oldTasks = quest == null ? new HashMap<>()
            : copyEntries(
                quest.getTasks()
                    .getEntries());
        migrateTasks(oldTasks, prepared.tasks());
        if (quest == null) {
            quest = QuestDatabase.INSTANCE.createNew(definition.getUuid());
        }
        if (prepared.templateNbt() != null) {
            NBTTagCompound properties = (NBTTagCompound) prepared.templateNbt()
                .copy();
            properties.removeTag("tasks");
            properties.removeTag("rewards");
            quest.readFromNBT(properties);
        } else {
            quest.setProperty(NativeProps.NAME, definition.getId());
            quest.setProperty(NativeProps.DESC, "");
        }
        if (prepared.useDefinitionProperties()) {
            quest.setProperty(NativeProps.MAIN, definition.isMain());
            if (prepared.icon() != null) {
                quest.setProperty(NativeProps.ICON, prepared.icon());
            }
            quest.setRequirements(definition.getPrerequisites());
            for (UUID requirement : definition.getPrerequisites()) {
                quest.setRequirementType(requirement, definition.getRequirementType(requirement));
            }
        }
        replaceTasks(quest, prepared.tasks());
        applyRewards(quest, prepared.rewards());
    }

    public void applyTasks(IQuest quest, Map<Integer, ITask> oldTasks, Map<Integer, ITask> rebuiltTasks) {
        migrateTasks(oldTasks, rebuiltTasks);
        replaceTasks(quest, rebuiltTasks);
    }

    private void migrateTasks(Map<Integer, ITask> oldTasks, Map<Integer, ITask> rebuiltTasks) {
        rebuiltTasks.forEach((slot, task) -> TaskProgressMigrator.copyCompatibleProgress(oldTasks.get(slot), task));
    }

    private void replaceTasks(IQuest quest, Map<Integer, ITask> rebuiltTasks) {
        quest.getTasks()
            .reset();
        rebuiltTasks.entrySet()
            .stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(
                entry -> {
                    quest.getTasks()
                        .add(entry.getKey(), entry.getValue());
                });
    }

    public void applyRewards(IQuest quest, Map<Integer, IReward> rebuiltRewards) {
        quest.getRewards()
            .reset();
        rebuiltRewards.entrySet()
            .stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(
                entry -> quest.getRewards()
                    .add(entry.getKey(), entry.getValue()));
    }

    private BigItemStack loadItem(NBTTagCompound itemNbt) {
        BigItemStack stack = JsonHelper.JsonToItemStack(itemNbt);
        if (stack == null) {
            throw new IllegalArgumentException("Unable to load quest icon from NBT");
        }
        return stack;
    }

    private <T> Map<Integer, T> copyEntries(Iterable<DBEntry<T>> entries) {
        Map<Integer, T> result = new LinkedHashMap<>();
        for (DBEntry<T> entry : entries) {
            result.put(entry.getID(), entry.getValue());
        }
        return result;
    }

    /**
     * Runtime objects are mutable; each prepared quest is intended for a single application.
     */
    public record PreparedQuest(QuestDefinition definition, NBTTagCompound templateNbt, BigItemStack icon,
        Map<Integer, ITask> tasks, Map<Integer, IReward> rewards, boolean useDefinitionProperties) {

        public PreparedQuest {
            Objects.requireNonNull(definition, "definition");
            templateNbt = templateNbt == null ? null : (NBTTagCompound) templateNbt.copy();
            tasks = Collections.unmodifiableMap(new LinkedHashMap<>(tasks));
            rewards = Collections.unmodifiableMap(new LinkedHashMap<>(rewards));
        }
    }
}
