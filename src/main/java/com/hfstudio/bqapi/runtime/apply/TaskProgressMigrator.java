package com.hfstudio.bqapi.runtime.apply;

import net.minecraft.nbt.NBTTagCompound;

import betterquesting.api.questing.tasks.ITask;

public class TaskProgressMigrator {

    private TaskProgressMigrator() {}

    public static void copyCompatibleProgress(ITask source, ITask target) {
        if (source == null || target == null || source == target) {
            return;
        }
        if (!source.getFactoryID()
            .equals(target.getFactoryID())) {
            return;
        }

        NBTTagCompound progress = source.writeProgressToNBT(new NBTTagCompound(), null);
        target.readProgressFromNBT(progress, true);
    }
}
