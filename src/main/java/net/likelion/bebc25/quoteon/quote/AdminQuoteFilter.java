package net.likelion.bebc25.quoteon.quote;

import java.time.LocalDate;

record AdminQuoteFilter(
        LocalDate quoteDateFrom,
        LocalDate quoteDateTo,
        String authorName,
        String customerName,
        String productKeyword,
        String status
) {
}
