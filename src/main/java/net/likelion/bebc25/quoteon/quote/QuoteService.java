package net.likelion.bebc25.quoteon.quote;

import net.likelion.bebc25.quoteon.customer.CustomerAccessDeniedException;
import net.likelion.bebc25.quoteon.customer.CustomerNotActiveException;
import net.likelion.bebc25.quoteon.customer.CustomerNotFoundException;
import net.likelion.bebc25.quoteon.customer.CustomerRef;
import net.likelion.bebc25.quoteon.customer.CustomerRepository;
import net.likelion.bebc25.quoteon.product.ProductDetail;
import net.likelion.bebc25.quoteon.product.ProductNotActiveException;
import net.likelion.bebc25.quoteon.product.ProductNotFoundException;
import net.likelion.bebc25.quoteon.product.ProductRepository;
import net.likelion.bebc25.quoteon.quote.dto.CreateQuoteItemRequest;
import net.likelion.bebc25.quoteon.quote.dto.CreateQuoteRequest;
import net.likelion.bebc25.quoteon.quote.dto.QuotePageResponse;
import net.likelion.bebc25.quoteon.quote.dto.QuoteResponse;
import net.likelion.bebc25.quoteon.quote.dto.QuoteSummaryResponse;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class QuoteService {

    private static final int QUOTE_NUMBER_ATTEMPTS = 5;
    private static final int DEFAULT_PAGE = 0;
    private static final int MAX_PAGE_SIZE = 100;
    private static final BigDecimal VAT_RATE = new BigDecimal("0.10");

    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final QuoteRepository quoteRepository;
    private final TransactionTemplate transactionTemplate;

    public QuoteService(
            CustomerRepository customerRepository,
            ProductRepository productRepository,
            QuoteRepository quoteRepository,
            PlatformTransactionManager transactionManager
    ) {
        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
        this.quoteRepository = quoteRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public QuotePageResponse list(long memberId, int page, int size) {
        if (page < DEFAULT_PAGE) {
            throw new InvalidQuoteQueryException("페이지 번호는 0 이상이어야 합니다.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidQuoteQueryException("페이지 크기는 1 이상 100 이하여야 합니다.");
        }
        long totalElements = quoteRepository.countByMember(memberId);
        int totalPages = totalElements == 0 ? 0 : (int) ((totalElements + size - 1) / size);
        List<QuoteSummaryResponse> items = quoteRepository.findByMember(memberId, size, (long) page * size);
        return new QuotePageResponse(items, page, size, totalElements, totalPages);
    }

    public QuoteResponse get(long memberId, long quoteId) {
        QuoteAccess access = quoteRepository.findAccessById(quoteId)
                .orElseThrow(QuoteNotFoundException::new);
        if (access.memberId() != memberId) {
            throw new QuoteAccessDeniedException();
        }
        return access.quote();
    }

    public QuoteResponse create(long memberId, CreateQuoteRequest request) {
        QuoteResponse response = transactionTemplate.execute(status -> writeQuote(memberId, request));
        if (response == null) {
            throw new IllegalStateException("quote was not stored");
        }
        return response;
    }

    public QuoteResponse update(long memberId, long quoteId, CreateQuoteRequest request) {
        QuoteResponse response = transactionTemplate.execute(status -> replaceQuote(memberId, quoteId, request));
        if (response == null) {
            throw new IllegalStateException("quote was not stored");
        }
        return response;
    }

    private QuoteResponse writeQuote(long memberId, CreateQuoteRequest request) {
        PricedQuote priced = priceQuote(memberId, request, true);
        long quoteId = insertQuote(
                memberId,
                priced.customerId(),
                priced.supplyAmount(),
                priced.vatAmount(),
                priced.totalAmount(),
                request
        );
        quoteRepository.insertItems(quoteId, priced.lines());
        return quoteRepository.findById(quoteId)
                .orElseThrow(() -> new IllegalStateException("quote was not stored"));
    }

    private QuoteResponse replaceQuote(long memberId, long quoteId, CreateQuoteRequest request) {
        QuoteAccess access = quoteRepository.findAccessById(quoteId)
                .orElseThrow(QuoteNotFoundException::new);
        if (access.memberId() != memberId) {
            throw new QuoteAccessDeniedException();
        }
        if (!QuoteRepository.DRAFT.equals(access.quote().status())) {
            throw new QuoteNotEditableException();
        }
        PricedQuote priced = priceQuote(memberId, request, false);
        int updated = quoteRepository.updateQuote(
                quoteId,
                memberId,
                priced.customerId(),
                priced.supplyAmount(),
                priced.vatAmount(),
                priced.totalAmount(),
                request.quoteDate()
        );
        if (updated != 1) {
            throw new QuoteNotFoundException();
        }
        quoteRepository.deleteItems(quoteId);
        quoteRepository.insertItems(quoteId, priced.lines());
        return quoteRepository.findById(quoteId)
                .orElseThrow(() -> new IllegalStateException("quote was not stored"));
    }

    private PricedQuote priceQuote(long memberId, CreateQuoteRequest request, boolean requireActiveCustomer) {
        rejectDuplicateProducts(request.items());
        CustomerRef customer = customerRepository.findById(request.customerId())
                .orElseThrow(CustomerNotFoundException::new);
        if (customer.memberId() != memberId) {
            throw new CustomerAccessDeniedException();
        }
        if (requireActiveCustomer && !CustomerRepository.ACTIVE_STATUS.equals(customer.status())) {
            throw new CustomerNotActiveException();
        }

        List<QuoteItemWrite> lines = new ArrayList<>();
        BigDecimal supplyAmount = BigDecimal.ZERO.setScale(2);
        for (CreateQuoteItemRequest item : request.items()) {
            ProductDetail product = productRepository.findById(item.productId())
                    .orElseThrow(ProductNotFoundException::new);
            if (!ProductRepository.ON_SALE_STATUS.equals(product.status())) {
                throw new ProductNotActiveException();
            }
            BigDecimal amount = lineAmount(product.salePrice(), item.quantity());
            supplyAmount = supplyAmount.add(amount);
            lines.add(new QuoteItemWrite(
                    product.id(),
                    product.name(),
                    product.productCode(),
                    product.salePrice(),
                    item.quantity(),
                    amount
            ));
        }
        BigDecimal vatAmount = vatAmount(supplyAmount);
        return new PricedQuote(customer.id(), supplyAmount, vatAmount, supplyAmount.add(vatAmount), lines);
    }

    static BigDecimal lineAmount(BigDecimal salePrice, int quantity) {
        return salePrice.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
    }

    static BigDecimal vatAmount(BigDecimal supplyAmount) {
        // 공급가액의 10%를 원 단위로 반올림한다.
        return supplyAmount.multiply(VAT_RATE).setScale(0, RoundingMode.HALF_UP);
    }

    private void rejectDuplicateProducts(List<CreateQuoteItemRequest> items) {
        Set<Long> productIds = new HashSet<>();
        for (CreateQuoteItemRequest item : items) {
            if (!productIds.add(item.productId())) {
                throw new DuplicateProductException();
            }
        }
    }

    private long insertQuote(
            long memberId,
            long customerId,
            BigDecimal supplyAmount,
            BigDecimal vatAmount,
            BigDecimal totalAmount,
            CreateQuoteRequest request
    ) {
        DuplicateKeyException lastConflict = null;
        for (int attempt = 0; attempt < QUOTE_NUMBER_ATTEMPTS; attempt++) {
            String quoteNumber = QuoteNumbers.next(request.quoteDate());
            try {
                return quoteRepository.insertQuote(
                        quoteNumber,
                        memberId,
                        customerId,
                        supplyAmount,
                        vatAmount,
                        totalAmount,
                        request.quoteDate()
                );
            } catch (DuplicateKeyException exception) {
                lastConflict = exception;
            }
        }
        if (lastConflict == null) {
            throw new IllegalStateException("quote number was not generated");
        }
        throw lastConflict;
    }

    private record PricedQuote(
            long customerId,
            BigDecimal supplyAmount,
            BigDecimal vatAmount,
            BigDecimal totalAmount,
            List<QuoteItemWrite> lines
    ) {
    }
}
