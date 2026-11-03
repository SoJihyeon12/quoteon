package net.likelion.bebc25.quoteon.quote;

public class QuoteEmailSendException extends RuntimeException {

    public QuoteEmailSendException(Throwable cause) {
        super("견적서 이메일을 전송하지 못했습니다.", cause);
    }
}
