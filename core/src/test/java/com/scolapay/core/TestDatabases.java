package com.scolapay.core;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Un seul PostgreSQL pour toute la suite, et une base vierge pour les tests qui démarrent l'application eux-mêmes. */
final class TestDatabases {

    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6-alpine");

    static {
        POSTGRES.start();
    }

    private TestDatabases() {}

    static String createEmpty() {
        String name = "t_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection admin = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                Statement statement = admin.createStatement()) {
            statement.execute("CREATE DATABASE " + name);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432) + "/" + name;
    }

    static JdbcClient client(String url) {
        return JdbcClient.create(new DriverManagerDataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword()));
    }

    /** Démarrage réel : uniquement ce qu'exige la production, sous forme d'arguments de ligne de commande. */
    static List<String> applicationArguments(String url) {
        return List.of(
                "--spring.datasource.url=" + url,
                "--spring.datasource.username=" + POSTGRES.getUsername(),
                "--spring.datasource.password=" + POSTGRES.getPassword(),
                "--scolapay.security.jwt-secret=" + IntegrationTest.JWT_SECRET,
                "--scolapay.payments.identity-encryption-key=" + com.scolapay.core.payments.internal.CashIdentityFixtures.KEY,
                "--scolapay.bootstrap.operator-contact=" + IntegrationTest.OPERATOR_CONTACT,
                "--scolapay.bootstrap.operator-password=" + IntegrationTest.OPERATOR_PASSWORD);
    }
}
