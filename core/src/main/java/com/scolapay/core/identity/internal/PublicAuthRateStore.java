package com.scolapay.core.identity.internal;

import com.scolapay.core.identity.internal.AuthAbuseSettings.Budget;
import com.scolapay.core.identity.internal.AuthAbuseSettings.Endpoint;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.LongAdder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;

/** GCRA partagé : horloge PostgreSQL, débit régulier et rafale bornée, sans frontière de fenêtre doublée. */
@Component
public class PublicAuthRateStore {
    private static final Logger log = LoggerFactory.getLogger(PublicAuthRateStore.class);
    private final JdbcClient jdbc;
    private final TransactionTemplate tx;
    private final AuthAbuseSettings settings;
    private final AuthAbuseKeys keys;
    private final Semaphore sqlSlots;
    private final LongAdder storageFailures = new LongAdder();
    private final LongAdder limited = new LongAdder();
    private final LongAdder busy = new LongAdder();

    PublicAuthRateStore(JdbcClient jdbc, PlatformTransactionManager manager, AuthAbuseSettings settings, AuthAbuseKeys keys) {
        this.jdbc = jdbc; this.settings = settings; this.keys = keys;
        sqlSlots = new Semaphore(settings.counterConcurrency);
        tx = new TransactionTemplate(manager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.setTimeout(1);
    }

    public void ingress(Endpoint endpoint, String origin) {
        var limits = settings.limits(endpoint);
        int retry = admission(endpoint.scope, keys.slot("origin", origin), limits.origin(),
                endpoint.scope+1, 0, limits.global());
        if (retry > 0) { limited.increment(); throw AuthAbuseRejection.limited(retry); }
    }

    public void identity(Endpoint endpoint, String origin, String target) {
        var limits = settings.limits(endpoint);
        int retry = admission(endpoint.scope+2, keys.slot("pair", origin+"\0"+target), limits.pair(),
                endpoint.scope+3, keys.slot("target", target), limits.target());
        if (retry > 0) { limited.increment(); throw AuthAbuseRejection.limited(retry); }
    }

    private int admission(int scope, int slot, Budget budget, int secondScope, int secondSlot, Budget secondBudget) {
        if (!sqlSlots.tryAcquire()) { busy.increment(); throw AuthAbuseRejection.busy(); }
        try {
            Integer retry = tx.execute(status -> {
                jdbc.sql("SET LOCAL statement_timeout = '250ms'").update();
                jdbc.sql("SET LOCAL lock_timeout = '100ms'").update();
                int first = take(scope, slot, budget);
                return first > 0 ? first : take(secondScope, secondSlot, secondBudget);
            });
            if (retry == null) throw AuthAbuseRejection.unavailable();
            return retry;
        } catch (DataAccessException | TransactionException unavailable) {
            storageFailures.increment();
            throw AuthAbuseRejection.unavailable();
        } finally { sqlSlots.release(); }
    }

    private int take(int scope, int slot, Budget budget) {
        var accepted = jdbc.sql("""
                WITH tick AS (SELECT clock_timestamp() AS moment)
                INSERT INTO auth_abuse_buckets (scope, slot, tat)
                SELECT :scope, :slot, moment + :step * interval '1 microsecond' FROM tick
                ON CONFLICT (scope, slot) DO UPDATE
                SET tat = greatest(auth_abuse_buckets.tat, (SELECT moment FROM tick))
                          + :step * interval '1 microsecond'
                WHERE auth_abuse_buckets.tat - :tolerance * interval '1 microsecond'
                      <= (SELECT moment FROM tick)
                RETURNING slot
                """).param("scope", scope).param("slot", slot).param("step", budget.stepMicros())
                .param("tolerance", budget.toleranceMicros()).query(Integer.class).optional();
        if (accepted.isPresent()) return 0;
        return jdbc.sql("""
                SELECT greatest(1, ceil(extract(epoch FROM
                    (tat - :tolerance * interval '1 microsecond' - clock_timestamp()))))::integer
                FROM auth_abuse_buckets WHERE scope=:scope AND slot=:slot
                """).param("scope", scope).param("slot", slot).param("tolerance", budget.toleranceMicros())
                .query(Integer.class).single();
    }

    @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT5M")
    public void prune() {
        if (!sqlSlots.tryAcquire()) return;
        try {
            tx.executeWithoutResult(status -> jdbc.sql("""
                    WITH old AS (
                        SELECT scope, slot FROM auth_abuse_buckets WHERE tat <= clock_timestamp()
                        ORDER BY tat LIMIT 1000 FOR UPDATE SKIP LOCKED
                    )
                    DELETE FROM auth_abuse_buckets b USING old
                    WHERE b.scope=old.scope AND b.slot=old.slot
                    """).update());
        } catch (DataAccessException | TransactionException unavailable) { storageFailures.increment(); }
        finally { sqlSlots.release(); }
    }

    @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT1M")
    void report() {
        long failures = storageFailures.sumThenReset();
        long rejected = limited.sumThenReset();
        long saturated = busy.sumThenReset();
        if (failures + rejected + saturated > 0) {
            log.warn("Protection authentification : {} refus de débit, {} saturations, {} indisponibilités du stockage.",
                    rejected, saturated, failures);
        }
    }
}
