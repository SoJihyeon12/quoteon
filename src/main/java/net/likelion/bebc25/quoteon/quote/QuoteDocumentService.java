package net.likelion.bebc25.quoteon.quote;

import net.likelion.bebc25.quoteon.quote.dto.AdminQuoteDetailResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

@Service
public class QuoteDocumentService {

    private final AdminQuoteService adminQuoteService;
    private final QuoteRepository quoteRepository;
    private final QuoteDocumentRenderer renderer = new QuoteDocumentRenderer();

    public QuoteDocumentService(AdminQuoteService adminQuoteService, QuoteRepository quoteRepository) {
        this.adminQuoteService = adminQuoteService;
        this.quoteRepository = quoteRepository;
    }

    public QuoteFile pdf(long quoteId) {
        AdminQuoteDetailResponse quote = adminQuoteService.get(quoteId);
        return new QuoteFile(renderer.pdf(quote), filename(quote, "pdf"), MediaType.APPLICATION_PDF);
    }

    public QuoteFile image(long quoteId) {
        AdminQuoteDetailResponse quote = adminQuoteService.get(quoteId);
        byte[] pdf = renderer.pdf(quote);
        return new QuoteFile(renderer.png(pdf), filename(quote, "png"), MediaType.IMAGE_PNG);
    }

    public QuoteFile salesPdf(long memberId, long quoteId) {
        AdminQuoteDetailResponse quote = ownedQuote(memberId, quoteId);
        return new QuoteFile(renderer.salesPdf(quote), filename(quote, "pdf"), MediaType.APPLICATION_PDF);
    }

    public QuoteFile salesPng(long memberId, long quoteId) {
        AdminQuoteDetailResponse quote = ownedQuote(memberId, quoteId);
        byte[] pdf = renderer.salesPdf(quote);
        return new QuoteFile(renderer.png(pdf), filename(quote, "png"), MediaType.IMAGE_PNG);
    }

    public QuoteFile salesJpeg(long memberId, long quoteId) {
        AdminQuoteDetailResponse quote = ownedQuote(memberId, quoteId);
        byte[] pdf = renderer.salesPdf(quote);
        return new QuoteFile(renderer.jpeg(pdf), filename(quote, "jpg"), MediaType.IMAGE_JPEG);
    }

    private AdminQuoteDetailResponse ownedQuote(long memberId, long quoteId) {
        QuoteAccess access = quoteRepository.findAccessById(quoteId)
                .orElseThrow(QuoteNotFoundException::new);
        if (access.memberId() != memberId) {
            throw new QuoteAccessDeniedException();
        }
        return adminQuoteService.get(quoteId);
    }

    private static String filename(AdminQuoteDetailResponse quote, String extension) {
        return "견적서_" + quote.quoteNumber() + "." + extension;
    }
}
