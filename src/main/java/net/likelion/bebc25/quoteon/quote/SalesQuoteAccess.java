package net.likelion.bebc25.quoteon.quote;

final class SalesQuoteAccess {

    private SalesQuoteAccess() {
    }

    static QuoteAccess requireAccessible(QuoteAccess access, long memberId) {
        if (access.memberId() != memberId) {
            throw new QuoteAccessDeniedException();
        }
        if (access.isPrivate()) {
            throw new QuotePrivateException();
        }
        return access;
    }
}
