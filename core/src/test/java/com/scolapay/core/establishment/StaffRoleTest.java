package com.scolapay.core.establishment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StaffRoleTest {

    @Test
    void lAdministrateurDetientToutesLesPermissions() {
        for (Permission permission : Permission.values()) {
            assertThat(StaffRole.ESTABLISHMENT_ADMIN.grants(permission)).as(permission.name()).isTrue();
        }
    }

    @Test
    void seulLAdministrateurGereLePersonnel() {
        for (StaffRole role : StaffRole.values()) {
            assertThat(role.grants(Permission.MEMBERS_MANAGE)).as(role.name()).isEqualTo(role == StaffRole.ESTABLISHMENT_ADMIN);
        }
    }

    @Test
    void leDirecteurConfirmeSansGererLesFinances() {
        assertThat(StaffRole.DIRECTOR.grants(Permission.BILLING_APPROVE)).isTrue();
        assertThat(StaffRole.DIRECTOR.grants(Permission.BILLING_READ)).isTrue();
        assertThat(StaffRole.DIRECTOR.grants(Permission.BILLING_REASONS_READ)).isTrue();
        for (Permission permission : new Permission[] {
            Permission.BILLING_ADJUST, Permission.BILLING_ISSUE, Permission.FEES_MANAGE, Permission.FEES_PUBLISH,
            Permission.AUDIT_READ, Permission.MEMBERS_MANAGE, Permission.STUDENTS_MANAGE, Permission.ESTABLISHMENT_MANAGE
        }) {
            assertThat(StaffRole.DIRECTOR.grants(permission)).as(permission.name()).isFalse();
        }
    }

    @Test
    void leCaissierNeLitNiNeConfirmeLesMotifs() {
        assertThat(StaffRole.CASHIER.grants(Permission.BILLING_READ)).isTrue();
        assertThat(StaffRole.CASHIER.grants(Permission.BILLING_REASONS_READ)).isFalse();
        assertThat(StaffRole.CASHIER.grants(Permission.BILLING_APPROVE)).isFalse();
        assertThat(StaffRole.CASHIER.grants(Permission.BILLING_ADJUST)).isFalse();
    }

    @Test
    void seulsLAdministrateurEtLeComptableGerentLesPersonnesLiees() {
        for (StaffRole role : StaffRole.values()) {
            assertThat(role.grants(Permission.BILLING_PARTIES_MANAGE)).as(role.name())
                    .isEqualTo(role == StaffRole.ESTABLISHMENT_ADMIN || role == StaffRole.ACCOUNTANT);
        }
    }

    /** Confirmer ou opérer sans lire le motif reviendrait à décider à l'aveugle. */
    @Test
    void quiOpereOuConfirmeLitLesMotifs() {
        for (StaffRole role : StaffRole.values()) {
            if (role.grants(Permission.BILLING_APPROVE) || role.grants(Permission.BILLING_ADJUST)) {
                assertThat(role.grants(Permission.BILLING_REASONS_READ)).as(role.name()).isTrue();
            }
        }
    }

    @Test
    void lesRolesDeControleNeModifientRien() {
        for (StaffRole role : new StaffRole[] {StaffRole.AUDITOR, StaffRole.ACCOUNTANT, StaffRole.CASHIER, StaffRole.DIRECTOR}) {
            assertThat(role.grants(Permission.STUDENTS_MANAGE)).as(role.name()).isFalse();
            assertThat(role.grants(Permission.ENROLLMENTS_MANAGE)).as(role.name()).isFalse();
            assertThat(role.grants(Permission.GUARDIANS_MANAGE)).as(role.name()).isFalse();
            assertThat(role.grants(Permission.ACADEMIC_CATALOG_MANAGE)).as(role.name()).isFalse();
        }
    }
}
