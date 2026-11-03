package net.likelion.bebc25.quoteon.quote;

import jakarta.validation.Valid;
import net.likelion.bebc25.quoteon.quote.dto.CreateQuoteRequest;
import net.likelion.bebc25.quoteon.quote.dto.QuoteEmailHistoryListResponse;
import net.likelion.bebc25.quoteon.quote.dto.QuoteEmailResponse;
import net.likelion.bebc25.quoteon.quote.dto.QuotePageResponse;
import net.likelion.bebc25.quoteon.quote.dto.QuoteResponse;
import net.likelion.bebc25.quoteon.quote.dto.SendQuoteEmailRequest;
import net.likelion.bebc25.quoteon.security.MemberPrincipal;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/quotes")
public class QuoteController {

    private final QuoteService quoteService;
    private final QuoteDocumentService quoteDocumentService;
    private final QuoteEmailService quoteEmailService;

    public QuoteController(
            QuoteService quoteService,
            QuoteDocumentService quoteDocumentService,
            QuoteEmailService quoteEmailService
    ) {
        this.quoteService = quoteService;
        this.quoteDocumentService = quoteDocumentService;
        this.quoteEmailService = quoteEmailService;
    }

    @GetMapping
    public QuotePageResponse list(
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) String quoteDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return quoteService.list(currentMemberId(), customerName, quoteDate, page, size);
    }

    @GetMapping("/{id}")
    public QuoteResponse detail(@PathVariable long id) {
        return quoteService.get(currentMemberId(), id);
    }

    @PutMapping("/{id}")
    public QuoteResponse update(@PathVariable long id, @Valid @RequestBody CreateQuoteRequest request) {
        return quoteService.update(currentMemberId(), id, request);
    }

    @PostMapping
    public ResponseEntity<QuoteResponse> create(@Valid @RequestBody CreateQuoteRequest request) {
        QuoteResponse response = quoteService.create(currentMemberId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/email")
    public QuoteEmailResponse email(@PathVariable long id, @Valid @RequestBody SendQuoteEmailRequest request) {
        return quoteEmailService.send(currentMemberId(), id, request);
    }

    @GetMapping("/{id}/email/histories")
    public QuoteEmailHistoryListResponse emailHistories(@PathVariable long id) {
        return quoteEmailService.histories(currentMemberId(), id);
    }

    @PostMapping("/{id}/email/histories/{historyId}/retry")
    public QuoteEmailResponse retryEmail(
            @PathVariable long id,
            @PathVariable long historyId,
            @Valid @RequestBody SendQuoteEmailRequest request
    ) {
        return quoteEmailService.retry(currentMemberId(), id, historyId, request);
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable long id) {
        return file(quoteDocumentService.salesPdf(currentMemberId(), id));
    }

    @GetMapping("/{id}/jpeg")
    public ResponseEntity<byte[]> jpeg(@PathVariable long id) {
        return file(quoteDocumentService.salesJpeg(currentMemberId(), id));
    }

    @GetMapping("/{id}/png")
    public ResponseEntity<byte[]> png(@PathVariable long id) {
        return file(quoteDocumentService.salesPng(currentMemberId(), id));
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

    private long currentMemberId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        MemberPrincipal principal = (MemberPrincipal) authentication.getPrincipal();
        return principal.id();
    }
}
