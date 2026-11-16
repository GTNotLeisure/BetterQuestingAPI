package com.hfstudio.bqapi.runtime.apply;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;

import com.hfstudio.bqapi.api.definition.ChapterDefinition;
import com.hfstudio.bqapi.api.definition.QuestPlacementDefinition;

import betterquesting.api.properties.NativeProps;
import betterquesting.api.questing.IQuestLine;
import betterquesting.api.questing.IQuestLineEntry;
import betterquesting.api.utils.BigItemStack;
import betterquesting.api.utils.JsonHelper;
import betterquesting.api.utils.UuidConverter;
import betterquesting.questing.QuestLineDatabase;
import betterquesting.questing.QuestLineEntry;

public class ChapterApplier {

    public void apply(ChapterDefinition definition, Set<UUID> managedQuestUuids) {
        NBTTagCompound template = definition.getTemplateNbt();
        NBTTagCompound iconNbt = template == null ? definition.getIconNbt() : null;
        BigItemStack icon = iconNbt == null ? null : JsonHelper.JsonToItemStack(iconNbt);
        if (iconNbt != null && icon == null) {
            throw new IllegalArgumentException("Unable to load chapter icon from NBT");
        }
        IQuestLine chapter = QuestLineDatabase.INSTANCE.get(definition.getUuid());
        if (chapter == null) {
            chapter = QuestLineDatabase.INSTANCE.createNew(definition.getUuid());
        }

        Map<UUID, IQuestLineEntry> unmanagedPlacements = new LinkedHashMap<>();
        for (Map.Entry<UUID, IQuestLineEntry> entry : chapter.entrySet()) {
            if (!managedQuestUuids.contains(entry.getKey())) {
                unmanagedPlacements.put(entry.getKey(), entry.getValue());
            }
        }
        if (template != null) {
            chapter.readFromNBT(template);
        } else {
            chapter.setProperty(NativeProps.NAME, definition.getId());
            chapter.setProperty(NativeProps.DESC, "");
            if (icon != null) {
                chapter.setProperty(NativeProps.ICON, icon);
            }
        }
        chapter.clear();

        for (QuestPlacementDefinition placement : definition.getPlacements()) {
            chapter.put(
                placement.getQuest()
                    .getUuid(),
                new QuestLineEntry(placement.getX(), placement.getY(), placement.getSizeX(), placement.getSizeY()));
        }
        unmanagedPlacements.forEach(chapter::putIfAbsent);

        int index = resolveOrderIndex(definition);
        if (index >= 0) {
            QuestLineDatabase.INSTANCE.setOrderIndex(definition.getUuid(), index);
        } else {
            QuestLineDatabase.INSTANCE.getOrderIndex(definition.getUuid());
        }
    }

    private int resolveOrderIndex(ChapterDefinition definition) {
        String afterEncoded = definition.getOrderAfterEncoded();
        if (afterEncoded == null || afterEncoded.isEmpty()) {
            return -1;
        }

        UUID afterId = UuidConverter.decodeUuid(afterEncoded);
        int afterIndex = QuestLineDatabase.INSTANCE.getOrderIndex(afterId);
        if (afterIndex < 0) {
            return QuestLineDatabase.INSTANCE.getOrderedEntries()
                .size();
        }
        return afterIndex + 1;
    }
}
