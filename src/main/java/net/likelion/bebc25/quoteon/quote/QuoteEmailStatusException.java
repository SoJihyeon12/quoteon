package net.likelion.bebc25.quoteon.quote;

public class QuoteEmailStatusException extends RuntimeException {

    public QuoteEmailStatusException(Throwable cause) {
        super("이메일은 전송되었지만 견적 상태를 저장하지 못했습니다.", cause);
    }
}
