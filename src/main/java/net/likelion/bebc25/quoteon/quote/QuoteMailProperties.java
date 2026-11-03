package net.likelion.bebc25.quoteon.quote;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "quoteon.mail")
public record QuoteMailProperties(
        String host,
        String port,
        String username,
        String password,
        String smtpAuth,
        String starttlsEnable
) {

    public List<String> missing() {
        List<String> names = new ArrayList<>();
        if (blank(host)) {
            names.add("MAIL_HOST");
        }
        if (blank(port) || !port.chars().allMatch(Character::isDigit)) {
            names.add("MAIL_PORT");
        }
        if (blank(username)) {
            names.add("MAIL_USERNAME");
        }
        if (blank(password)) {
            names.add("MAIL_PASSWORD");
        }
        if (blank(smtpAuth)) {
            names.add("MAIL_PROPERTIES_MAIL_SMTP_AUTH");
        }
        if (blank(starttlsEnable)) {
            names.add("MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE");
        }
        return List.copyOf(names);
    }

    public int portNumber() {
        return Integer.parseInt(port);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
