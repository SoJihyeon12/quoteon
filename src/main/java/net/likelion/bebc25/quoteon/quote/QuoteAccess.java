package net.likelion.bebc25.quoteon.quote;

import net.likelion.bebc25.quoteon.quote.dto.QuoteResponse;

record QuoteAccess(
        long memberId,
        QuoteResponse quote
) {
}
