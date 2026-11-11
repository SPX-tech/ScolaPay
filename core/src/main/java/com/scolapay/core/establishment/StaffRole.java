package com.scolapay.core.establishment;

import static com.scolapay.core.establishment.Permission.ACADEMIC_CATALOG_MANAGE;
import static com.scolapay.core.establishment.Permission.ACADEMIC_YEARS_MANAGE;
import static com.scolapay.core.establishment.Permission.AUDIT_READ;
import static com.scolapay.core.establishment.Permission.BILLING_ADJUST;
import static com.scolapay.core.establishment.Permission.BILLING_APPROVE;
import static com.scolapay.core.establishment.Permission.BILLING_ISSUE;
import static com.scolapay.core.establishment.Permission.BILLING_PARTIES_MANAGE;
import static com.scolapay.core.establishment.Permission.BILLING_READ;
import static com.scolapay.core.establishment.Permission.BILLING_REASONS_READ;
import static com.scolapay.core.establishment.Permission.CASH_RECEPTIONS_READ;
import static com.scolapay.core.establishment.Permission.CASH_RECEPTIONS_RECORD;
import static com.scolapay.core.establishment.Permission.CASH_ALLOCATE;
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
    CASHIER(EnumSet.of(ESTABLISHMENT_READ, STUDENTS_READ, BILLING_READ, CASH_RECEPTIONS_READ, CASH_RECEPTIONS_RECORD, CASH_ALLOCATE)),
    ACCOUNTANT(EnumSet.of(
            ESTABLISHMENT_READ, STUDENTS_READ, AUDIT_READ, FEES_MANAGE, BILLING_READ, BILLING_ISSUE, BILLING_ADJUST, BILLING_APPROVE, BILLING_REASONS_READ, BILLING_PARTIES_MANAGE, CASH_RECEPTIONS_READ, CASH_RECEPTIONS_RECORD, CASH_ALLOCATE)),
    AUDITOR(EnumSet.of(ESTABLISHMENT_READ, STUDENTS_READ, AUDIT_READ, BILLING_READ, BILLING_REASONS_READ, CASH_RECEPTIONS_READ)),
    // Confirme ce qu'un autre a préparé, sans pouvoir opérer : il reste la seconde personne d'une petite école.
    DIRECTOR(EnumSet.of(ESTABLISHMENT_READ, STUDENTS_READ, BILLING_READ, BILLING_APPROVE, BILLING_REASONS_READ, CASH_RECEPTIONS_READ));

    private final Set<Permission> permissions;

    StaffRole(Set<Permission> permissions) {
        this.permissions = Set.copyOf(permissions);
    }

    public boolean grants(Permission permission) {
        return permissions.contains(permission);
    }
}
