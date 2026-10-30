package net.likelion.bebc25.quoteon.support;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public class TestDatabaseCreator implements EnvironmentPostProcessor, Ordered {

    static final String TEST_DATABASE = "quote_db";
    static final String APPLICATION_DATABASE = "quoteon_db";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String url = environment.getProperty("spring.datasource.url", "");
        String mode = environment.getProperty("spring.sql.init.mode", "");
        if (!"always".equalsIgnoreCase(mode)) {
            return;
        }
        if (!url.contains("/" + TEST_DATABASE)) {
            throw new IllegalStateException("테스트 스키마 초기화는 " + TEST_DATABASE + "에서만 실행할 수 있습니다.");
        }
        if (url.contains("/" + APPLICATION_DATABASE)) {
            throw new IllegalStateException("테스트 스키마 초기화는 " + APPLICATION_DATABASE + "에서 실행할 수 없습니다.");
        }
        createTestDatabase(environment, url);
    }

    private static void createTestDatabase(ConfigurableEnvironment environment, String testUrl) {
        String username = environment.getProperty("spring.datasource.username");
        String password = environment.getProperty("spring.datasource.password");
        String adminUrl = testUrl.replace("/" + TEST_DATABASE, "/" + APPLICATION_DATABASE);
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            try (Connection connection = DriverManager.getConnection(adminUrl, username, password);
                 Statement statement = connection.createStatement()) {
                statement.execute(
                        "CREATE DATABASE IF NOT EXISTS " + TEST_DATABASE
                                + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
                );
            }
        } catch (Exception exception) {
            throw new IllegalStateException("테스트 데이터베이스를 준비하지 못했습니다.", exception);
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
