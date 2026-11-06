package com.scolapay.core.establishment;

import static com.scolapay.core.establishment.Permission.ACADEMIC_CATALOG_MANAGE;
import static com.scolapay.core.establishment.Permission.ACADEMIC_YEARS_MANAGE;
import static com.scolapay.core.establishment.Permission.AUDIT_READ;
import static com.scolapay.core.establishment.Permission.BILLING_ISSUE;
import static com.scolapay.core.establishment.Permission.BILLING_READ;
import static com.scolapay.core.establishment.Permission.ENROLLMENTS_MANAGE;
import static com.scolapay.core.establishment.Permission.ESTABLISHMENT_READ;
import static com.scolapay.core.establishment.Permission.FEES_MANAGE;
import static com.scolapay.core.establishment.Permission.GUARDIANS_MANAGE;
import static com.scolapay.core.establishment.Permission.STUDENTS_MANAGE;
import static com.scolapay.core.establishment.Permission.STUDENTS_READ;

import java.util.EnumSet;
import java.util.Set;

/** Rôles du personnel. Ils n'ont de sens qu'au sein de l'établissement de l'adhésion qui les porte. */
public enum StaffRole {
    ESTABLISHMENT_ADMIN(EnumSet.allOf(Permission.class)),
    REGISTRAR(EnumSet.of(
            ESTABLISHMENT_READ, ACADEMIC_YEARS_MANAGE, ACADEMIC_CATALOG_MANAGE, STUDENTS_READ, STUDENTS_MANAGE, ENROLLMENTS_MANAGE, GUARDIANS_MANAGE)),
    CASHIER(EnumSet.of(ESTABLISHMENT_READ, STUDENTS_READ, BILLING_READ)),
    ACCOUNTANT(EnumSet.of(ESTABLISHMENT_READ, STUDENTS_READ, AUDIT_READ, FEES_MANAGE, BILLING_READ, BILLING_ISSUE)),
    AUDITOR(EnumSet.of(ESTABLISHMENT_READ, STUDENTS_READ, AUDIT_READ, BILLING_READ));

    private final Set<Permission> permissions;

    StaffRole(Set<Permission> permissions) {
        this.permissions = Set.copyOf(permissions);
    }

    public boolean grants(Permission permission) {
        return permissions.contains(permission);
    }
}
