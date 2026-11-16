package com.hfstudio.bqapi.runtime.apply;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Map;

import org.junit.Test;

import com.hfstudio.bqapi.api.builder.RewardBuilders;
import com.hfstudio.bqapi.api.builder.TaskBuilders;

import betterquesting.api.questing.rewards.IReward;
import betterquesting.api.questing.tasks.ITask;
import bq_standard.tasks.TaskCheckbox;
import bq_standard.tasks.TaskLocation;

public class DefinitionSlotsTest {

    @Test
    public void automaticTasksDoNotOccupyLaterExplicitSlots() {
        Map<Integer, ITask> tasks = new TaskApplier().buildTasks(
            Arrays.asList(
                TaskBuilders.checkbox("test:auto")
                    .build(),
                TaskBuilders.location("test:explicit")
                    .slot(0)
                    .build()));

        assertEquals(2, tasks.size());
        assertTrue(tasks.get(0) instanceof TaskLocation);
        assertTrue(tasks.get(1) instanceof TaskCheckbox);
    }

    @Test
    public void automaticRewardsDoNotOccupyLaterExplicitSlots() {
        Map<Integer, IReward> rewards = new RewardApplier().buildRewards(
            Arrays.asList(
                RewardBuilders.item("test:auto")
                    .build(),
                RewardBuilders.item("test:explicit")
                    .slot(0)
                    .build()));

        assertEquals(2, rewards.size());
        assertTrue(rewards.containsKey(0));
        assertTrue(rewards.containsKey(1));
    }
}
