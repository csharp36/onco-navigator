package com.onconavigator.domain.dto;

import java.util.List;

/**
 * Edge modifications in a child template diff.
 *
 * <p>Specifies prerequisite edges to remove from the parent graph and new edges to add.
 * Edge changes are applied after step removals, overrides, and additions during the
 * template merge operation.
 *
 * <p>The compact constructor ensures null-safety: null lists default to empty immutable lists.
 *
 * @param remove edges from the parent template to remove (by stepId pair)
 * @param add    new edges to introduce in the merged result
 */
public record EdgeChanges(List<EdgeRef> remove, List<EdgeRef> add) {

    public EdgeChanges {
        remove = remove != null ? remove : List.of();
        add = add != null ? add : List.of();
    }
}
