package net.likelion.bebc25.quoteon.quote;

public class QuoteDocumentException extends RuntimeException {

    public QuoteDocumentException(Throwable cause) {
        super(cause);
    }

    public QuoteDocumentException(String message) {
        super(message);
    }
}
