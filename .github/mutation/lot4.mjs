// Défauts du lot 4 de la Mission 003 : obligations, échéanciers, journal et émission idempotente.
// Chaque étape : [fichier, texte à trouver exactement une fois, remplacement].
export default {
  controls: 3,
  mutations: [
    {
      "id": "issue-without-confirmation",
      "steps": [
        [
          "core/src/main/java/com/scolapay/core/billing/internal/ObligationService.java",
          "UUID confirmed = event.confirmedFeeCatalogVersionId();",
          "UUID confirmed = catalogs.publishedVersion(event.establishmentId(), event.academicYearId()).map(Version::id).orElse(null);"
        ]
      ]
    },
    {
      "id": "issue-after-withdrawal",
      "steps": [
        [
          "core/src/main/java/com/scolapay/core/billing/internal/ObligationService.java",
          "if (!enrollment.active()) {\n            audit.record(",
          "if (\"__\".isEmpty()) {\n            audit.record("
        ]
      ]
    },
    {
      "id": "automatic-includes-on-request",
      "steps": [
        [
          "core/src/main/java/com/scolapay/core/billing/internal/ObligationService.java",
          "return FeeResolution.applicableTo(academicUnitId, catalogs.items(establishmentId, version.id()), units).stream()\n                .filter(item -> \"AUTOMATIC\".equals(item.application()))\n",
          "return FeeResolution.applicableTo(academicUnitId, catalogs.items(establishmentId, version.id()), units).stream()\n"
        ]
      ]
    },
    {
      "id": "batch-includes-on-request",
      "steps": [
        [
          "core/src/main/java/com/scolapay/core/billing/internal/ObligationService.java",
          "List<Item> items = catalogs.items(establishmentId, version.id()).stream()\n                .filter(item -> \"AUTOMATIC\".equals(item.application()))\n",
          "List<Item> items = catalogs.items(establishmentId, version.id()).stream()\n"
        ]
      ]
    },
    {
      "id": "batch-ignores-issued",
      "steps": [
        [
          "core/src/main/java/com/scolapay/core/billing/internal/ObligationService.java",
          "if (issued.contains(List.of(enrollment.studentId(), item.feeDefinitionId()))) {",
          "if (\"__\".isEmpty()) {"
        ]
      ]
    },
    {
      "id": "token-ignored",
      "steps": [
        [
          "core/src/main/java/com/scolapay/core/billing/internal/ObligationService.java",
          "if (!token(version, plan).equals(previewToken)) {",
          "if (\"__\".isEmpty()) {"
        ]
      ]
    },
    {
      "id": "token-ignores-content",
      "mustFail": ["ObligationTest.unLotConfirmeApresUnRemplacementDEleveAEffectifConstantNEmetRien"],
      "steps": [
        [
          "core/src/main/java/com/scolapay/core/billing/internal/ObligationService.java",
          "planned.enrollment().id() + \"|\" + planned.item().id() + \"|\" + planned.item().amountMinor()",
          "\"x\""
        ]
      ]
    },
    {
      "id": "batch-closed-year-open",
      "steps": [
        [
          "core/src/main/java/com/scolapay/core/billing/internal/ObligationService.java",
          "if (year.closed()) {",
          "if (\"__\".isEmpty()) {"
        ]
      ]
    },
    {
      "id": "no-on-conflict",
      "steps": [
        [
          "core/src/main/java/com/scolapay/core/billing/internal/ObligationRepository.java",
          "                ON CONFLICT ON CONSTRAINT obligations_one_per_charge DO NOTHING\n",
          ""
        ]
      ]
    },
    {
      "id": "key-includes-catalog-version",
      "steps": [
        [
          "core/src/main/resources/db/migration/V5__obligations.sql",
          "CONSTRAINT obligations_one_per_charge UNIQUE (establishment_id, student_record_id, fee_definition_id),",
          "CONSTRAINT obligations_one_per_charge UNIQUE (establishment_id, student_record_id, fee_definition_id, catalog_version_id),"
        ]
      ],
      "mustFail": [
        "ObligationTest.uneReinscriptionApresUneNouvelleVersionNeRefacturePasLaCharge",
        "ObligationTest.laCleDUniciteNeContientNiLaVersionNiLInscription"
      ]
    },
    {
      "id": "key-includes-enrollment",
      "steps": [
        [
          "core/src/main/resources/db/migration/V5__obligations.sql",
          "CONSTRAINT obligations_one_per_charge UNIQUE (establishment_id, student_record_id, fee_definition_id),",
          "CONSTRAINT obligations_one_per_charge UNIQUE (establishment_id, student_record_id, fee_definition_id, enrollment_id),"
        ]
      ],
      "mustFail": [
        "ObligationTest.unRetraitPuisUneReinscriptionNeRefacturentPasLaMemeCharge",
        "ObligationTest.laCleDUniciteNeContientNiLaVersionNiLInscription"
      ]
    },
    {
      "id": "status-always-open",
      "steps": [
        [
          "core/src/main/java/com/scolapay/core/billing/internal/ObligationService.java",
          "return dueMinor > 0 ? OPEN : WAIVED;",
          "return OPEN;"
        ]
      ]
    },
    {
      "id": "due-from-issued-amount",
      "steps": [
        [
          "core/src/main/java/com/scolapay/core/billing/internal/ObligationRepository.java",
          "(SELECT COALESCE(sum(e.amount_minor), 0) FROM obligation_entries e WHERE e.obligation_id = o.id) AS due_minor,",
          "o.issued_amount_minor AS due_minor,"
        ]
      ]
    },
    {
      "id": "balance-from-issued-amount",
      "steps": [
        [
          "core/src/main/java/com/scolapay/core/billing/internal/ObligationRepository.java",
          "CAST(sum(b.due_minor) AS bigint) AS due_minor,",
          "CAST(sum(o.issued_amount_minor) AS bigint) AS due_minor,"
        ]
      ]
    },
    {
      "id": "next-due-when-cleared",
      "steps": [
        [
          "core/src/main/java/com/scolapay/core/billing/internal/ObligationRepository.java",
          "AND i.due_on >= :today AND b.due_minor > 0",
          "AND i.due_on >= :today"
        ]
      ]
    },
    {
      "id": "next-due-in-past",
      "steps": [
        [
          "core/src/main/java/com/scolapay/core/billing/internal/ObligationRepository.java",
          "WHERE i.obligation_id = o.id AND i.due_on >= :today AND b.due_minor > 0",
          "WHERE i.obligation_id = o.id AND b.due_minor > 0"
        ]
      ]
    },
    {
      "id": "list-by-reader",
      "steps": [
        [
          "core/src/main/java/com/scolapay/core/billing/internal/ObligationService.java",
          "public List<Obligation> list(UUID establishmentId, UUID academicYearId, UUID studentId, PageQuery page) {\n        access.require(establishmentId, Permission.BILLING_READ);",
          "public List<Obligation> list(UUID establishmentId, UUID academicYearId, UUID studentId, PageQuery page) {\n        access.require(establishmentId, Permission.ESTABLISHMENT_READ);"
        ]
      ]
    },
    {
      "id": "balance-by-reader",
      "steps": [
        [
          "core/src/main/java/com/scolapay/core/billing/internal/ObligationService.java",
          "public List<Balance> balances(UUID establishmentId, UUID studentId) {\n        access.require(establishmentId, Permission.BILLING_READ);",
          "public List<Balance> balances(UUID establishmentId, UUID studentId) {\n        access.require(establishmentId, Permission.ESTABLISHMENT_READ);"
        ]
      ]
    },
    {
      "id": "batch-preview-by-reader",
      "steps": [
        [
          "core/src/main/java/com/scolapay/core/billing/internal/ObligationService.java",
          "public IssuancePreview issuancePreview(UUID establishmentId, UUID academicYearId, UUID academicUnitId) {\n        access.require(establishmentId, Permission.BILLING_ISSUE);",
          "public IssuancePreview issuancePreview(UUID establishmentId, UUID academicYearId, UUID academicUnitId) {\n        access.require(establishmentId, Permission.ESTABLISHMENT_READ);"
        ]
      ]
    },
    {
      "id": "batch-confirm-by-reader",
      "steps": [
        [
          "core/src/main/java/com/scolapay/core/billing/internal/ObligationService.java",
          "UUID catalogVersionId, String previewToken) {\n        UUID actorId = access.require(establishmentId, Permission.BILLING_ISSUE);",
          "UUID catalogVersionId, String previewToken) {\n        UUID actorId = access.require(establishmentId, Permission.BILLING_READ);"
        ]
      ]
    },
    {
      "id": "manual-by-reader",
      "steps": [
        [
          "core/src/main/java/com/scolapay/core/billing/internal/ObligationService.java",
          "public ManualIssue issueManually(UUID establishmentId, UUID enrollmentId, UUID feeDefinitionId) {\n        UUID actorId = access.require(establishmentId, Permission.BILLING_ISSUE);",
          "public ManualIssue issueManually(UUID establishmentId, UUID enrollmentId, UUID feeDefinitionId) {\n        UUID actorId = access.require(establishmentId, Permission.BILLING_READ);"
        ]
      ]
    },
    {
      "id": "find-across-tenants",
      "steps": [
        [
          "core/src/main/java/com/scolapay/core/billing/internal/ObligationRepository.java",
          "SELECT + \"WHERE o.establishment_id = :establishmentId AND o.id = :id\")\n                .param(\"establishmentId\", establishmentId)\n",
          "SELECT + \"WHERE o.id = :id\")\n"
        ]
      ]
    },
    {
      "id": "list-across-tenants",
      "steps": [
        [
          "core/src/main/java/com/scolapay/core/billing/internal/ObligationRepository.java",
          "WHERE o.establishment_id = :establishmentId AND o.academic_year_id = :yearId\n                \"\"\" + filter",
          "WHERE o.academic_year_id = :yearId\n                \"\"\" + filter"
        ],
        [
          "core/src/main/java/com/scolapay/core/billing/internal/ObligationRepository.java",
          "                .param(\"establishmentId\", establishmentId)\n                .param(\"yearId\", academicYearId)\n                .param(\"limit\", page.limit())",
          "                .param(\"yearId\", academicYearId)\n                .param(\"limit\", page.limit())"
        ]
      ]
    },
    {
      "id": "confirmation-not-verified",
      "steps": [
        [
          "core/src/main/java/com/scolapay/core/schooling/internal/SchoolingService.java",
          "            feeConfirmation.verify(establishmentId, academicYearId, confirmedFeeCatalogVersionId);\n",
          ""
        ]
      ]
    },
    {
      "id": "confirmation-accepts-any",
      "steps": [
        [
          "core/src/main/java/com/scolapay/core/billing/internal/PublishedCatalogConfirmation.java",
          "                .filter(version -> version.id().equals(confirmedCatalogVersionId))\n",
          ""
        ]
      ]
    },
    {
      "id": "unit-scope-ignored",
      "steps": [
        [
          "core/src/main/java/com/scolapay/core/billing/internal/ObligationService.java",
          ".filter(unit -> isWithin(unit, academicUnitId, units))",
          ".filter(unit -> unit != null)"
        ]
      ]
    },
    {
      "id": "size-limit-ignored",
      "steps": [
        [
          "core/src/main/java/com/scolapay/core/billing/internal/ObligationService.java",
          "if (enrollments.size() > maxIssuanceSize) {",
          "if (\"__\".isEmpty()) {"
        ]
      ]
    },
    {
      "id": "manual-ended-enrollment",
      "steps": [
        [
          "core/src/main/java/com/scolapay/core/billing/internal/ObligationService.java",
          "if (!enrollment.active()) {\n            throw ApiException.conflict(\"ENROLLMENT_NOT_ACTIVE\"",
          "if (\"__\".isEmpty()) {\n            throw ApiException.conflict(\"ENROLLMENT_NOT_ACTIVE\""
        ]
      ]
    },
    {
      "id": "preview-hides-already-issued",
      "steps": [
        [
          "core/src/main/java/com/scolapay/core/billing/internal/ObligationService.java",
          "boolean alreadyIssued = studentId != null && issued.contains(List.of(studentId, item.feeDefinitionId()));",
          "boolean alreadyIssued = false;"
        ]
      ]
    },
    {
      "id": "fee-total-not-summed",
      "steps": [
        [
          "core/src/main/java/com/scolapay/core/billing/internal/ObligationService.java",
          "a.obligations() + 1, Math.addExact(a.totalMinor(), b.totalMinor())",
          "a.obligations() + 1, a.totalMinor()"
        ]
      ]
    },
    {
      "id": "db-settlement-allowed",
      "steps": [
        [
          "core/src/main/resources/db/migration/V5__obligations.sql",
          "kind IN ('ISSUED', 'DISCOUNT', 'ADJUSTMENT')",
          "kind IN ('ISSUED', 'DISCOUNT', 'ADJUSTMENT', 'SETTLEMENT')"
        ],
        [
          "core/src/main/resources/db/migration/V5__obligations.sql",
          "OR (kind = 'ADJUSTMENT' AND amount_minor <> 0 AND reason IS NOT NULL)),",
          "OR (kind = 'ADJUSTMENT' AND amount_minor <> 0 AND reason IS NOT NULL)\n    OR kind = 'SETTLEMENT'),"
        ]
      ]
    },
    {
      "id": "db-negative-open",
      "steps": [
        [
          "core/src/main/resources/db/migration/V5__obligations.sql",
          "IF due + NEW.amount_minor < 0 THEN",
          "IF FALSE THEN"
        ]
      ]
    },
    {
      "id": "db-entries-no-lock",
      "mustFail": ["ObligationTest.uneEcritureConcurrenteAttendPuisNeRendJamaisLeDuNegatif"],
      "steps": [
        [
          "core/src/main/resources/db/migration/V5__obligations.sql",
          "  PERFORM 1 FROM obligations WHERE id = NEW.obligation_id FOR UPDATE;\n",
          ""
        ]
      ]
    },
    {
      "id": "db-obligations-mutable",
      "steps": [
        [
          "core/src/main/resources/db/migration/V5__obligations.sql",
          "CREATE TRIGGER obligations_immutable BEFORE UPDATE OR DELETE ON obligations\n  FOR EACH ROW EXECUTE FUNCTION forbid_mutation();\n",
          ""
        ]
      ]
    },
    {
      "id": "db-installments-mutable",
      "steps": [
        [
          "core/src/main/resources/db/migration/V5__obligations.sql",
          "CREATE TRIGGER obligation_installments_immutable BEFORE UPDATE OR DELETE ON obligation_installments\n  FOR EACH ROW EXECUTE FUNCTION forbid_mutation();\n",
          ""
        ]
      ]
    },
    {
      "id": "db-entries-mutable",
      "steps": [
        [
          "core/src/main/resources/db/migration/V5__obligations.sql",
          "CREATE TRIGGER obligation_entries_immutable BEFORE UPDATE OR DELETE ON obligation_entries\n  FOR EACH ROW EXECUTE FUNCTION forbid_mutation();\n",
          ""
        ]
      ]
    },
    {
      "id": "db-entries-truncate",
      "steps": [
        [
          "core/src/main/resources/db/migration/V5__obligations.sql",
          "CREATE TRIGGER obligation_entries_no_truncate BEFORE TRUNCATE ON obligation_entries\n  FOR EACH STATEMENT EXECUTE FUNCTION forbid_mutation();\n",
          ""
        ],
        [
          "core/src/main/resources/db/migration/V5__obligations.sql",
          "CREATE TRIGGER obligations_no_truncate BEFORE TRUNCATE ON obligations\n  FOR EACH STATEMENT EXECUTE FUNCTION forbid_mutation();\n",
          ""
        ]
      ]
    },
    {
      "id": "db-complete-open",
      "steps": [
        [
          "core/src/main/resources/db/migration/V5__obligations.sql",
          "CREATE CONSTRAINT TRIGGER obligations_complete AFTER INSERT ON obligations\n  DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION obligations_complete();\n",
          ""
        ]
      ]
    },
    {
      "id": "db-installment-added-later",
      "steps": [
        [
          "core/src/main/resources/db/migration/V5__obligations.sql",
          "CREATE CONSTRAINT TRIGGER obligation_installments_complete AFTER INSERT ON obligation_installments\n  DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION obligations_complete();\n",
          ""
        ]
      ]
    },
    {
      "id": "db-no-issued-entry-check",
      "steps": [
        [
          "core/src/main/resources/db/migration/V5__obligations.sql",
          "IF NOT EXISTS (SELECT 1 FROM obligation_entries WHERE obligation_id = target AND kind = 'ISSUED' AND amount_minor = issued) THEN",
          "IF FALSE THEN"
        ]
      ]
    },
    {
      "id": "db-enrollment-fk-simple",
      "steps": [
        [
          "core/src/main/resources/db/migration/V5__obligations.sql",
          "FOREIGN KEY (establishment_id, enrollment_id, student_record_id, academic_year_id)\n    REFERENCES enrollments (establishment_id, id, student_record_id, academic_year_id),",
          "FOREIGN KEY (establishment_id, enrollment_id) REFERENCES enrollments (establishment_id, id),"
        ]
      ]
    },
    {
      "id": "db-reason-optional",
      "steps": [
        [
          "core/src/main/resources/db/migration/V5__obligations.sql",
          "OR (kind = 'DISCOUNT' AND amount_minor < 0 AND reason IS NOT NULL)",
          "OR (kind = 'DISCOUNT' AND amount_minor < 0)"
        ]
      ]
    },
    {
      "id": "db-many-issued",
      "steps": [
        [
          "core/src/main/resources/db/migration/V5__obligations.sql",
          "CREATE UNIQUE INDEX obligation_entries_one_issued ON obligation_entries (obligation_id) WHERE kind = 'ISSUED';\n",
          ""
        ]
      ]
    },
    {
      "id": "pending-by-reader",
      "steps": [
        [
          "core/src/main/java/com/scolapay/core/billing/internal/ObligationService.java",
          "public PendingIssuances pendingIssuances(UUID establishmentId, UUID academicYearId, UUID academicUnitId, PageQuery page) {\n        access.require(establishmentId, Permission.BILLING_READ);",
          "public PendingIssuances pendingIssuances(UUID establishmentId, UUID academicYearId, UUID academicUnitId, PageQuery page) {\n        access.require(establishmentId, Permission.ESTABLISHMENT_READ);"
        ]
      ]
    },
    {
      "id": "pending-count-is-page-size",
      "steps": [
        [
          "core/src/main/java/com/scolapay/core/billing/internal/ObligationService.java",
          "return new PendingIssuances(published.get(), byEnrollment.size(), total(plan), pending);",
          "return new PendingIssuances(published.get(), pending.size(), total(plan), pending);"
        ]
      ]
    },
    {
      "id": "pending-page-ignored",
      "steps": [
        [
          "core/src/main/java/com/scolapay/core/billing/internal/ObligationService.java",
          ".skip(page.offset())\n                .limit(page.limit())\n",
          ""
        ]
      ]
    }
  ],
};
