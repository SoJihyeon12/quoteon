package net.likelion.bebc25.quoteon.quote;

import java.util.List;

public class QuoteMailNotConfiguredException extends RuntimeException {

    public QuoteMailNotConfiguredException(List<String> missing) {
        super("메일 발송 설정이 없습니다: " + String.join(", ", missing));
    }
}
