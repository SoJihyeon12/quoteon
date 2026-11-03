package net.likelion.bebc25.quoteon.quote;

import net.likelion.bebc25.quoteon.quote.dto.AdminQuoteDetailResponse;
import net.likelion.bebc25.quoteon.quote.dto.AdminQuotePageResponse;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/admin/quotes")
public class AdminQuoteController {

    private final AdminQuoteService adminQuoteService;
    private final QuoteDocumentService quoteDocumentService;

    public AdminQuoteController(AdminQuoteService adminQuoteService, QuoteDocumentService quoteDocumentService) {
        this.adminQuoteService = adminQuoteService;
        this.quoteDocumentService = quoteDocumentService;
    }

    @GetMapping
    public AdminQuotePageResponse list(
            @RequestParam(required = false) String quoteDateFrom,
            @RequestParam(required = false) String quoteDateTo,
            @RequestParam(required = false) String authorName,
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) String productKeyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return adminQuoteService.list(
                quoteDateFrom,
                quoteDateTo,
                authorName,
                customerName,
                productKeyword,
                status,
                page,
                size
        );
    }

    @GetMapping("/{quoteId}")
    public AdminQuoteDetailResponse detail(@PathVariable long quoteId) {
        return adminQuoteService.get(quoteId);
    }

    @DeleteMapping("/{quoteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long quoteId) {
        adminQuoteService.delete(quoteId);
    }

    @GetMapping("/{quoteId}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable long quoteId) {
        return file(quoteDocumentService.pdf(quoteId));
    }

    @GetMapping("/{quoteId}/image")
    public ResponseEntity<byte[]> image(@PathVariable long quoteId) {
        return file(quoteDocumentService.image(quoteId));
    }

    private static ResponseEntity<byte[]> file(QuoteFile file) {
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(file.filename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(file.mediaType())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(file.bytes());
    }
}
