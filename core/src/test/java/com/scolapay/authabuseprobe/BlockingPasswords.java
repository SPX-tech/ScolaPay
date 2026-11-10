package com.scolapay.authabuseprobe;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Hors du scan applicatif : une opération réelle retenue par le test pour vérifier l'admission. */
@Configuration(proxyBeanMethods = false)
public class BlockingPasswords {
    public static class ControlledEncoder implements PasswordEncoder {
        public final CountDownLatch entered = new CountDownLatch(1);
        public final CountDownLatch release = new CountDownLatch(1);
        private final PasswordEncoder delegate = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        @Override public String encode(CharSequence password) { return delegate.encode(password); }
        @Override public boolean matches(CharSequence password, String encoded) {
            if (password.toString().equals("retenir-cet-essai")) {
                entered.countDown();
                try {
                    if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Sonde non libérée");
                } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return false; }
            }
            return delegate.matches(password, encoded);
        }
    }
    @Bean @Primary public ControlledEncoder controlledEncoder() { return new ControlledEncoder(); }
}
