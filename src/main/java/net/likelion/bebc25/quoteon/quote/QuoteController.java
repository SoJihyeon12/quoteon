package net.likelion.bebc25.quoteon.quote;

import jakarta.validation.Valid;
import net.likelion.bebc25.quoteon.quote.dto.CreateQuoteRequest;
import net.likelion.bebc25.quoteon.quote.dto.QuoteResponse;
import net.likelion.bebc25.quoteon.security.MemberPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/quotes")
public class QuoteController {

    private final QuoteService quoteService;

    public QuoteController(QuoteService quoteService) {
        this.quoteService = quoteService;
    }

    @PostMapping
    public ResponseEntity<QuoteResponse> create(@Valid @RequestBody CreateQuoteRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        MemberPrincipal principal = (MemberPrincipal) authentication.getPrincipal();
        QuoteResponse response = quoteService.create(principal.id(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
