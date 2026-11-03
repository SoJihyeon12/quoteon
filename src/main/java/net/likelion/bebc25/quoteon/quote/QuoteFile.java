package net.likelion.bebc25.quoteon.quote;

import org.springframework.http.MediaType;

public record QuoteFile(
        byte[] bytes,
        String filename,
        MediaType mediaType
) {
}
