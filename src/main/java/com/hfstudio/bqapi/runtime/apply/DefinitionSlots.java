package com.hfstudio.bqapi.runtime.apply;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.ToIntFunction;

public class DefinitionSlots {

    private DefinitionSlots() {}

    /**
     * A slot of -1 requests automatic allocation; all explicit slots take priority.
     */
    public static <T> Map<Integer, T> assign(Iterable<T> definitions, ToIntFunction<T> slotReader, String kind) {
        Objects.requireNonNull(definitions, "definitions");
        Objects.requireNonNull(slotReader, "slotReader");
        List<SlottedDefinition<T>> values = new ArrayList<>();
        Set<Integer> occupied = new HashSet<>();
        for (T definition : definitions) {
            T value = Objects.requireNonNull(definition, kind + " definition");
            int slot = slotReader.applyAsInt(value);
            if (slot < -1) {
                throw new IllegalArgumentException("Invalid " + kind + " slot: " + slot);
            }
            if (slot >= 0 && !occupied.add(slot)) {
                throw new IllegalArgumentException("Duplicate " + kind + " slot: " + slot);
            }
            values.add(new SlottedDefinition<>(value, slot));
        }

        Map<Integer, T> result = new LinkedHashMap<>();
        int nextAutoSlot = 0;
        for (SlottedDefinition<T> value : values) {
            int slot = value.slot();
            if (slot < 0) {
                while (occupied.contains(nextAutoSlot)) {
                    nextAutoSlot++;
                }
                slot = nextAutoSlot++;
                occupied.add(slot);
            }
            result.put(slot, value.definition());
        }
        return result;
    }

    private record SlottedDefinition<T> (T definition, int slot) {}
}
