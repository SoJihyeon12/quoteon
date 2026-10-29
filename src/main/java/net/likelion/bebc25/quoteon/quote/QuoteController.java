package net.likelion.bebc25.quoteon.quote;

import jakarta.validation.Valid;
import net.likelion.bebc25.quoteon.quote.dto.CreateQuoteRequest;
import net.likelion.bebc25.quoteon.quote.dto.QuotePageResponse;
import net.likelion.bebc25.quoteon.quote.dto.QuoteResponse;
import net.likelion.bebc25.quoteon.security.MemberPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/quotes")
public class QuoteController {

    private final QuoteService quoteService;

    public QuoteController(QuoteService quoteService) {
        this.quoteService = quoteService;
    }

    @GetMapping
    public QuotePageResponse list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return quoteService.list(currentMemberId(), page, size);
    }

    @GetMapping("/{id}")
    public QuoteResponse detail(@PathVariable long id) {
        return quoteService.get(currentMemberId(), id);
    }

    @PostMapping
    public ResponseEntity<QuoteResponse> create(@Valid @RequestBody CreateQuoteRequest request) {
        QuoteResponse response = quoteService.create(currentMemberId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    private long currentMemberId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        MemberPrincipal principal = (MemberPrincipal) authentication.getPrincipal();
        return principal.id();
    }
}
