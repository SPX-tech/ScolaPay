/** Journal d'audit immuable, écrit dans la transaction du changement qu'il documente. */
@org.springframework.modulith.ApplicationModule(
        displayName = "Audit",
        allowedDependencies = {"shared"})
package com.scolapay.core.audit;
