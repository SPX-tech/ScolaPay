package com.scolapay.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;

/** Fait échouer la CI si une dépendance franchit une frontière de module non déclarée. */
class ModularityTest {

    private final ApplicationModules modules = ApplicationModules.of(ScolaPayCoreApplication.class);

    @Test
    void lesFrontieresDesModulesSontRespectees() {
        modules.verify();
    }

    @Test
    void lesModulesAttendusExistent() {
        assertThat(modules.stream().map(ApplicationModule::getIdentifier).map(Object::toString))
                .containsExactlyInAnyOrder("shared", "audit", "identity", "establishment", "schooling", "billing", "notifications", "payments");
    }
}
