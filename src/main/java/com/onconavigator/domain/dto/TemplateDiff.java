package com.onconavigator.domain.dto;

import java.util.List;

/**
 * Diff-based child template JSONB structure (D-05).
 *
 * <p>A child template's {@code template_data} column stores this structure instead of
 * a flat step array. The merge engine uses it to resolve parent steps + child diff into
 * the final flat {@code List<PathwayStep>} at fork time.
 *
 * <p>Sections:
 * <ul>
 *   <li>{@code overrides} -- field-level changes to existing parent steps</li>
 *   <li>{@code additions} -- new steps added by the child template</li>
 *   <li>{@code removals} -- stepIds of parent steps to exclude entirely</li>
 *   <li>{@code edgeChanges} -- prerequisite edge modifications (add/remove)</li>
 * </ul>
 *
 * <p>The compact constructor ensures null-safety: null sections default to empty values.
 *
 * @param overrides   field-level overrides for existing parent steps
 * @param additions   new steps to add to the merged template
 * @param removals    stepIds of parent steps to remove
 * @param edgeChanges prerequisite edge modifications
 */
public record TemplateDiff(
        List<StepOverride> overrides,
        List<PathwayStep> additions,
        List<String> removals,
        EdgeChanges edgeChanges
) {

    public TemplateDiff {
        overrides = overrides != null ? overrides : List.of();
        additions = additions != null ? additions : List.of();
        removals = removals != null ? removals : List.of();
        edgeChanges = edgeChanges != null ? edgeChanges : new EdgeChanges(List.of(), List.of());
    }
}
