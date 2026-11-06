package com.scolapay.core.billing.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.scolapay.core.billing.internal.FeeCatalogRepository.Item;
import com.scolapay.core.schooling.AcademicCatalog.UnitSummary;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FeeResolutionTest {

    private static final UUID PROGRAM = UUID.randomUUID();
    private static final UUID TRACK = UUID.randomUUID();
    private static final UUID LEVEL = UUID.randomUUID();
    private static final UUID ELSEWHERE = UUID.randomUUID();
    private static final Map<UUID, UnitSummary> UNITS = Map.of(
            PROGRAM, unit(PROGRAM, null, "PROGRAM"),
            TRACK, unit(TRACK, PROGRAM, "PROGRAM"),
            LEVEL, unit(LEVEL, TRACK, "LEVEL"),
            ELSEWHERE, unit(ELSEWHERE, null, "LEVEL"));

    private static UnitSummary unit(UUID id, UUID parent, String kind) {
        return new UnitSummary(id, parent, kind, "C", "N", "ACTIVE");
    }

    private static final Map<String, UUID> DEFINITIONS = new java.util.concurrent.ConcurrentHashMap<>();

    private static Item item(String code, UUID unit, long amount) {
        UUID definition = DEFINITIONS.computeIfAbsent(code, ignored -> UUID.randomUUID());
        return new Item(UUID.randomUUID(), definition, code, "L", "OTHER", "AUTOMATIC", amount, UUID.randomUUID(), unit, List.of());
    }

    private static List<Long> amounts(UUID unit, List<Item> items) {
        return FeeResolution.applicableTo(unit, items, UNITS).stream().map(Item::amountMinor).toList();
    }

    @Test
    void leNiveauPrimeSurSesProgrammesEtLEtablissement() {
        assertThat(amounts(LEVEL, List.of(item("A", null, 1), item("A", PROGRAM, 2), item("A", TRACK, 3), item("A", LEVEL, 4))))
                .containsExactly(4L);
    }

    @Test
    void leProgrammeLePlusProcheLEmporte() {
        assertThat(amounts(LEVEL, List.of(item("A", null, 1), item("A", PROGRAM, 2), item("A", TRACK, 3)))).containsExactly(3L);
        assertThat(amounts(LEVEL, List.of(item("A", null, 1), item("A", PROGRAM, 2)))).containsExactly(2L);
    }

    @Test
    void lEtablissementSertDeRepliEtUneUniteEtrangereNeCompteJamais() {
        assertThat(amounts(LEVEL, List.of(item("A", null, 1), item("A", ELSEWHERE, 9)))).containsExactly(1L);
        assertThat(amounts(LEVEL, List.of(item("A", ELSEWHERE, 9)))).isEmpty();
    }

    /** Deux lignes d'une même charge se départagent même si leurs codes diffèrent. */
    @Test
    void laResolutionSuitLaChargeEtNonLeCode() {
        UUID definition = UUID.randomUUID();
        Item general = new Item(UUID.randomUUID(), definition, "ANCIEN", "L", "OTHER", "AUTOMATIC", 1, UUID.randomUUID(), null, List.of());
        Item specific = new Item(UUID.randomUUID(), definition, "NOUVEAU", "L", "OTHER", "AUTOMATIC", 2, UUID.randomUUID(), LEVEL, List.of());

        assertThat(FeeResolution.applicableTo(LEVEL, List.of(general, specific), UNITS)).containsExactly(specific);
    }

    @Test
    void chaqueCodeEstResoluSeparementEtDansUnOrdreStable() {
        assertThat(FeeResolution.applicableTo(LEVEL, List.of(item("B", null, 1), item("A", LEVEL, 2)), UNITS).stream().map(Item::feeCode))
                .containsExactly("A", "B");
    }
}
