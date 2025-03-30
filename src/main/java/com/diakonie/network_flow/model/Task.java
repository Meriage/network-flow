package com.diakonie.network_flow.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents a single task or activity in the project network diagram.
 * Uses Lombok to reduce boilerplate code.
 */
@Data
@NoArgsConstructor
public class Task {

    private String id;
    private String description;
    private int duration;
    //* Please ensure non-null during JSON parsing!
    private List<String> predecessorIds = new ArrayList<>();

    private int earliestStartDate = 0;
    private int earliestFinishDate = 0;
    private int latestStartDate = Integer.MAX_VALUE;
    private int latestFinishDate = Integer.MAX_VALUE;
    private int totalFloat = 0;
    private int freeFloat = 0;
    private boolean isCritical = false;

    public Task(String id, String description, int duration, List<String> predecessorIds) {
        this.id = id;
        this.description = description;
        this.duration = duration;
        this.predecessorIds = (predecessorIds != null) ? new ArrayList<>(predecessorIds) : new ArrayList<>();
    }

    // Lombok's @Data will generate:
    // - Getters for all fields (for example: getId(), getDescription(), getEarliestStartDate())
    // - Setters for all non-final fields (for example: setId(...), setDescription(...), setEarliestStartDate(...))
    // - equals() and hashCode() implementations (based on all non-static fields)
    // - toString() implementation

    // Note: While @Data can generate equals() and hashCode(),
    // explicit implementations based solely on the 'id' field are provided below
    // to ensure Task identity is determined only by its unique ID

    // The predecessorIds list is explicitily initialized and checked in the setter
    // to ensure it is never null. This prevents NullPointerExceptions,
    // especially when handling data from external sources like JSON
    public void setPredecessorIds(List<String> predecessorIds) {
        this.predecessorIds = (predecessorIds != null) ? new ArrayList<>(predecessorIds) : new ArrayList<>();
    }

    // --- Utility Methods ---
    @Override
    public String toString() {
        return "Task{" +
            "id='" + id + '\'' +
            ", description='" + description + '\'' +
            ", duration=" + duration +
            ", predecessors=" + predecessorIds +
            ", EST=" + earliestStartDate +
            ", EFT=" + earliestFinishDate +
            ", LST=" + latestStartDate +
            ", LFT=" + latestFinishDate +
            ", TF=" + totalFloat +
            ", FF=" + freeFloat +
            ", isCritical=" + isCritical +
            '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Task task = (Task) o;

        return Objects.equals(id, task.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}