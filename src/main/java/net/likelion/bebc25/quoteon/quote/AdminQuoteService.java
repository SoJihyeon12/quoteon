package net.likelion.bebc25.quoteon.quote;

import net.likelion.bebc25.quoteon.quote.dto.AdminQuoteDetailResponse;
import net.likelion.bebc25.quoteon.quote.dto.AdminQuotePageResponse;
import net.likelion.bebc25.quoteon.quote.dto.AdminQuoteSummaryResponse;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;

@Service
public class AdminQuoteService {

    private static final int DEFAULT_PAGE = 0;
    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> STATUSES = Set.of("DRAFT", "SENT");

    private final AdminQuoteRepository adminQuoteRepository;

    public AdminQuoteService(AdminQuoteRepository adminQuoteRepository) {
        this.adminQuoteRepository = adminQuoteRepository;
    }

    public AdminQuotePageResponse list(
            String quoteDateFrom,
            String quoteDateTo,
            String authorName,
            String customerName,
            String productKeyword,
            String status,
            int page,
            int size
    ) {
        if (page < DEFAULT_PAGE) {
            throw new InvalidQuoteQueryException("페이지 번호는 0 이상이어야 합니다.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidQuoteQueryException("페이지 크기는 1 이상 100 이하여야 합니다.");
        }
        LocalDate from = parseDate(quoteDateFrom, "작성일 시작일");
        LocalDate to = parseDate(quoteDateTo, "작성일 종료일");
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidQuoteQueryException("작성일 시작일은 종료일보다 늦을 수 없습니다.");
        }
        String normalizedStatus = normalize(status);
        if (normalizedStatus != null && !STATUSES.contains(normalizedStatus)) {
            throw new InvalidQuoteQueryException("견적 상태는 DRAFT 또는 SENT만 사용할 수 있습니다.");
        }
        AdminQuoteFilter filter = new AdminQuoteFilter(
                from,
                to,
                normalize(authorName),
                normalize(customerName),
                normalize(productKeyword),
                normalizedStatus
        );
        long totalElements = adminQuoteRepository.count(filter);
        int totalPages = totalElements == 0 ? 0 : (int) ((totalElements + size - 1) / size);
        List<AdminQuoteSummaryResponse> items = adminQuoteRepository.find(filter, size, (long) page * size);
        return new AdminQuotePageResponse(items, page, size, totalElements, totalPages);
    }

    public AdminQuoteDetailResponse get(long quoteId) {
        return adminQuoteRepository.findDetail(quoteId).orElseThrow(QuoteNotFoundException::new);
    }

    public void delete(long quoteId) {
        if (adminQuoteRepository.deleteById(quoteId) == 0) {
            throw new QuoteNotFoundException();
        }
    }

    private static LocalDate parseDate(String value, String label) {
        String normalized = normalize(value);
        if (normalized == null) {
            return null;
        }
        try {
            return LocalDate.parse(normalized);
        } catch (DateTimeParseException exception) {
            throw new InvalidQuoteQueryException(label + " 형식을 확인해 주세요.");
        }
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
