package com.scolapay.core.billing.internal;

import com.scolapay.core.billing.internal.FeeCatalogRepository.Item;
import com.scolapay.core.schooling.AcademicCatalog.UnitSummary;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Détermine les frais d'une unité académique. Pour une même charge, la ligne la plus proche de l'unité
 * l'emporte : l'unité elle-même, puis ses programmes en remontant, puis l'établissement entier. Une
 * unité reçoit donc au plus une ligne par charge, sans ambiguïté possible.
 */
final class FeeResolution {

    private FeeResolution() {}

    static List<Item> applicableTo(UUID unitId, List<Item> items, Map<UUID, UnitSummary> units) {
        List<UUID> lineage = lineage(unitId, units);
        Set<UUID> definitions = items.stream().map(Item::feeDefinitionId).collect(Collectors.toSet());
        List<Item> applicable = new ArrayList<>();
        for (UUID definition : definitions) {
            nearest(definition, lineage, items).ifPresent(applicable::add);
        }
        applicable.sort(Comparator.comparing(Item::feeCode));
        return applicable;
    }

    private static java.util.Optional<Item> nearest(UUID definition, List<UUID> lineage, List<Item> items) {
        for (UUID unit : lineage) {
            for (Item item : items) {
                if (item.feeDefinitionId().equals(definition) && Objects.equals(item.academicUnitId(), unit)) {
                    return java.util.Optional.of(item);
                }
            }
        }
        return items.stream()
                .filter(item -> item.feeDefinitionId().equals(definition) && item.academicUnitId() == null)
                .findFirst();
    }

    private static List<UUID> lineage(UUID unitId, Map<UUID, UnitSummary> units) {
        List<UUID> lineage = new ArrayList<>();
        UnitSummary current = units.get(unitId);
        while (current != null) {
            lineage.add(current.id());
            current = current.parentId() == null ? null : units.get(current.parentId());
        }
        return lineage;
    }
}
