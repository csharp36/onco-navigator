package com.onconavigator.service;

import com.onconavigator.domain.dto.AnchorType;
import com.onconavigator.domain.dto.EdgeChanges;
import com.onconavigator.domain.dto.EdgeRef;
import com.onconavigator.domain.dto.PathwayStep;
import com.onconavigator.domain.dto.StepOverride;
import com.onconavigator.domain.dto.TemplateDiff;
import com.onconavigator.domain.enums.CareEventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Pure-function merge engine for template inheritance (D-04, D-05, D-06).
 *
 * <p>Resolves parent steps + child diff into a flat {@code List<PathwayStep>}.
 * Stateless and side-effect-free -- operates only on the passed parameters.
 *
 * <p>Algorithm order (per RESEARCH.md Pattern 2):
 * <ol>
 *   <li>REMOVALS: filter out parent steps whose stepId is in diff.removals()</li>
 *   <li>OVERRIDES: apply field-level changes from diff.overrides()</li>
 *   <li>ADDITIONS: append new steps from diff.additions()</li>
 *   <li>EDGE CHANGES: modify prerequisites per diff.edgeChanges()</li>
 *   <li>EDGE VALIDATION: remove dangling prerequisite references</li>
 *   <li>RENUMBER: reassign stepNumber sequentially</li>
 * </ol>
 *
 * <p>PHI safety: This service handles only template configuration data (stepIds, clinical
 * process definitions). No patient data is accessed or logged.
 */
@Service
public class TemplateMergeService {

    private static final Logger log = LoggerFactory.getLogger(TemplateMergeService.class);

    /**
     * Merges parent steps with a child diff to produce a flat step list.
     *
     * @param parentSteps the parent template's full step list
     * @param diff        the child template's diff (overrides, additions, removals, edge changes)
     * @return merged flat step list with sequential stepNumbers
     */
    public List<PathwayStep> merge(List<PathwayStep> parentSteps, TemplateDiff diff) {
        // 1. REMOVALS: filter out parent steps whose stepId is in diff.removals
        Set<String> removedIds = new HashSet<>(diff.removals());
        List<PathwayStep> working = parentSteps.stream()
                .filter(s -> !removedIds.contains(s.stepId()))
                .collect(Collectors.toCollection(ArrayList::new));

        // 2. OVERRIDES: apply field-level changes to matching parent steps
        Map<String, StepOverride> overrideMap = diff.overrides().stream()
                .collect(Collectors.toMap(StepOverride::stepId, Function.identity()));
        working = working.stream()
                .map(s -> overrideMap.containsKey(s.stepId())
                        ? applyOverride(s, overrideMap.get(s.stepId()))
                        : s)
                .collect(Collectors.toCollection(ArrayList::new));

        // 3. ADDITIONS: append new steps
        working.addAll(diff.additions());

        // 4. EDGE CHANGES: modify prerequisites
        working = applyEdgeChanges(working, diff.edgeChanges());

        // 5. EDGE VALIDATION: remove dangling prerequisite references
        Set<String> validStepIds = working.stream()
                .map(PathwayStep::stepId)
                .collect(Collectors.toSet());
        working = working.stream()
                .map(s -> validatePrerequisites(s, validStepIds))
                .collect(Collectors.toCollection(ArrayList::new));

        // 6. RENUMBER: reassign stepNumbers sequentially
        for (int i = 0; i < working.size(); i++) {
            working.set(i, withStepNumber(working.get(i), i + 1));
        }

        return List.copyOf(working);
    }

    /**
     * Applies field-level overrides to a single step.
     * Only fields present in the override map are changed; others inherit from the parent.
     */
    private PathwayStep applyOverride(PathwayStep step, StepOverride override) {
        Map<String, Object> fields = override.fields();
        return new PathwayStep(
                step.stepId(),
                step.stepNumber(),
                fields.containsKey("name") ? (String) fields.get("name") : step.name(),
                fields.containsKey("description") ? (String) fields.get("description") : step.description(),
                fields.containsKey("eventType") ? CareEventType.valueOf((String) fields.get("eventType")) : step.eventType(),
                fields.containsKey("windowDays") ? ((Number) fields.get("windowDays")).intValue() : step.windowDays(),
                fields.containsKey("anchorType") ? AnchorType.valueOf((String) fields.get("anchorType")) : step.anchorType(),
                fields.containsKey("anchorStepId") ? (String) fields.get("anchorStepId") : step.anchorStepId(),
                fields.containsKey("required") ? (Boolean) fields.get("required") : step.required(),
                fields.containsKey("alertText") ? (String) fields.get("alertText") : step.alertText(),
                fields.containsKey("suggestedAction") ? (String) fields.get("suggestedAction") : step.suggestedAction(),
                fields.containsKey("prerequisites") ? castPrerequisites(fields.get("prerequisites")) : step.prerequisites()
        );
    }

    /**
     * Applies edge changes (remove/add) to step prerequisites.
     */
    private List<PathwayStep> applyEdgeChanges(List<PathwayStep> steps, EdgeChanges changes) {
        List<PathwayStep> result = new ArrayList<>(steps.size());
        for (PathwayStep step : steps) {
            List<String> prerequisites = new ArrayList<>(step.prerequisites());

            // Remove edges: if this step is the "to" target, remove "from" from prerequisites
            for (EdgeRef removeRef : changes.remove()) {
                if (removeRef.to().equals(step.stepId())) {
                    prerequisites.remove(removeRef.from());
                }
            }

            // Add edges: if this step is the "to" target, add "from" to prerequisites
            for (EdgeRef addRef : changes.add()) {
                if (addRef.to().equals(step.stepId())) {
                    if (!prerequisites.contains(addRef.from())) {
                        prerequisites.add(addRef.from());
                    }
                }
            }

            if (!prerequisites.equals(step.prerequisites())) {
                result.add(new PathwayStep(
                        step.stepId(), step.stepNumber(), step.name(), step.description(),
                        step.eventType(), step.windowDays(), step.anchorType(), step.anchorStepId(),
                        step.required(), step.alertText(), step.suggestedAction(),
                        List.copyOf(prerequisites)
                ));
            } else {
                result.add(step);
            }
        }
        return result;
    }

    /**
     * Validates that all prerequisites reference existing stepIds in the merged list.
     * Removes dangling references with a warning log.
     */
    private PathwayStep validatePrerequisites(PathwayStep step, Set<String> validStepIds) {
        if (step.prerequisites() == null || step.prerequisites().isEmpty()) {
            return step;
        }

        List<String> validPrereqs = step.prerequisites().stream()
                .filter(prereqId -> {
                    if (!validStepIds.contains(prereqId)) {
                        log.warn("Dangling prerequisite reference: step {} references non-existent step {}. Removing edge.",
                                step.stepId(), prereqId);
                        return false;
                    }
                    return true;
                })
                .toList();

        if (validPrereqs.size() != step.prerequisites().size()) {
            return new PathwayStep(
                    step.stepId(), step.stepNumber(), step.name(), step.description(),
                    step.eventType(), step.windowDays(), step.anchorType(), step.anchorStepId(),
                    step.required(), step.alertText(), step.suggestedAction(),
                    validPrereqs
            );
        }
        return step;
    }

    /**
     * Returns a new PathwayStep with an updated stepNumber.
     */
    private PathwayStep withStepNumber(PathwayStep step, int newNumber) {
        if (step.stepNumber() == newNumber) {
            return step;
        }
        return new PathwayStep(
                step.stepId(), newNumber, step.name(), step.description(),
                step.eventType(), step.windowDays(), step.anchorType(), step.anchorStepId(),
                step.required(), step.alertText(), step.suggestedAction(),
                step.prerequisites()
        );
    }

    /**
     * Safely casts an Object (from the override fields map) to List of String.
     */
    @SuppressWarnings("unchecked")
    private List<String> castPrerequisites(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            return list.stream()
                    .map(Object::toString)
                    .toList();
        }
        return List.of();
    }
}
