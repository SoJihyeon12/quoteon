package net.likelion.bebc25.quoteon.quote;

import java.time.LocalDate;

record QuoteListQuery(
        long memberId,
        String customerName,
        LocalDate quoteDate
) {
}
