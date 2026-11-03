package net.likelion.bebc25.quoteon.quote;

public interface QuoteMailGateway {

    void send(QuoteMailProperties properties, String recipient, String subject, String content, QuoteFile pdf);
}
