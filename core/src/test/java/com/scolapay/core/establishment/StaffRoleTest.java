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
    void lesRolesDeControleNeModifientRien() {
        for (StaffRole role : new StaffRole[] {StaffRole.AUDITOR, StaffRole.ACCOUNTANT, StaffRole.CASHIER}) {
            assertThat(role.grants(Permission.STUDENTS_MANAGE)).as(role.name()).isFalse();
            assertThat(role.grants(Permission.ENROLLMENTS_MANAGE)).as(role.name()).isFalse();
            assertThat(role.grants(Permission.GUARDIANS_MANAGE)).as(role.name()).isFalse();
            assertThat(role.grants(Permission.ACADEMIC_CATALOG_MANAGE)).as(role.name()).isFalse();
        }
    }
}
