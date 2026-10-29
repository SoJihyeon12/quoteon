package net.likelion.bebc25.quoteon.quote;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;

final class QuoteNumbers {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;

    private QuoteNumbers() {
    }

    static String next(LocalDate quoteDate) {
        byte[] bytes = new byte[8];
        RANDOM.nextBytes(bytes);
        return "Q-" + quoteDate.format(DAY) + "-" + HexFormat.of().formatHex(bytes);
    }
}
