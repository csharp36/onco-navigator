package com.onconavigator.domain.dto;

/**
 * Represents a directed edge between two pathway steps, identified by their stepIds.
 *
 * <p>Used within {@link EdgeChanges} to specify which prerequisite edges to add or remove
 * when a child template modifies the parent's step graph.
 *
 * @param from the stepId of the prerequisite (source) step
 * @param to   the stepId of the dependent (target) step
 */
public record EdgeRef(String from, String to) {
}
