package com.scolapay.outboxprobe;

import com.scolapay.core.schooling.StudentEnrolled;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Consomme le vrai événement, avec la même annotation que la future émission des obligations. Son
 * comportement est lu en base pour chaque élève, ce qui permet au test de le choisir à l'avance.
 */
public class ProbeListener {

    private final JdbcClient jdbc;
    private final TransactionTemplate independent;
    private final String instance;

    ProbeListener(JdbcClient jdbc, PlatformTransactionManager transactions, String instance) {
        this.jdbc = jdbc;
        this.independent = new TransactionTemplate(transactions);
        this.independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.instance = instance;
    }

    @ApplicationModuleListener
    public void on(StudentEnrolled event) throws InterruptedException {
        UUID student = event.studentId();
        long attempt = recordAttempt(student);
        switch (behavior(student)) {
            case "HANG_ONCE" -> {
                if (attempt == 1) {
                    hang();
                }
            }
            case "EFFECT_THEN_HANG_ONCE" -> {
                if (attempt == 1) {
                    independent.executeWithoutResult(status -> applyEffect(student));
                    hang();
                }
            }
            case "FAIL_ONCE" -> {
                if (attempt == 1) {
                    throw new IllegalStateException("échec provoqué");
                }
            }
            case "ALWAYS_FAIL" -> throw new IllegalStateException("échec provoqué");
            case "SLOW" -> Thread.sleep(1_000);
            default -> {}
        }
        applyEffect(student);
    }

    private void applyEffect(UUID student) {
        jdbc.sql("INSERT INTO probe_effects (student_id) VALUES (:student) ON CONFLICT DO NOTHING")
                .param("student", student)
                .update();
    }

    /** Dans sa propre transaction : l'essai reste compté même si le listener échoue ensuite. */
    private long recordAttempt(UUID student) {
        return independent.execute(status -> {
            jdbc.sql("INSERT INTO probe_attempts (student_id, instance) VALUES (:student, :instance)")
                    .param("student", student)
                    .param("instance", instance)
                    .update();
            return jdbc.sql("SELECT count(*) FROM probe_attempts WHERE student_id = :student")
                    .param("student", student)
                    .query(Long.class)
                    .single();
        });
    }

    private String behavior(UUID student) {
        return jdbc.sql("SELECT behavior FROM probe_behaviors WHERE student_id = :student")
                .param("student", student)
                .query(String.class)
                .optional()
                .orElse("NORMAL");
    }

    private static void hang() throws InterruptedException {
        Thread.sleep(Long.MAX_VALUE);
    }
}
