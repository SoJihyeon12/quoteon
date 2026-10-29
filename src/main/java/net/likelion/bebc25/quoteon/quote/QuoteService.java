package net.likelion.bebc25.quoteon.quote;

import net.likelion.bebc25.quoteon.customer.CustomerAccessDeniedException;
import net.likelion.bebc25.quoteon.customer.CustomerNotFoundException;
import net.likelion.bebc25.quoteon.customer.CustomerRef;
import net.likelion.bebc25.quoteon.customer.CustomerRepository;
import net.likelion.bebc25.quoteon.product.ProductDetail;
import net.likelion.bebc25.quoteon.product.ProductNotActiveException;
import net.likelion.bebc25.quoteon.product.ProductNotFoundException;
import net.likelion.bebc25.quoteon.product.ProductRepository;
import net.likelion.bebc25.quoteon.quote.dto.CreateQuoteItemRequest;
import net.likelion.bebc25.quoteon.quote.dto.CreateQuoteRequest;
import net.likelion.bebc25.quoteon.quote.dto.QuoteResponse;
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

    public QuoteResponse create(long memberId, CreateQuoteRequest request) {
        QuoteResponse response = transactionTemplate.execute(status -> writeQuote(memberId, request));
        if (response == null) {
            throw new IllegalStateException("quote was not stored");
        }
        return response;
    }

    private QuoteResponse writeQuote(long memberId, CreateQuoteRequest request) {
        rejectDuplicateProducts(request.items());
        CustomerRef customer = customerRepository.findById(request.customerId())
                .orElseThrow(CustomerNotFoundException::new);
        if (customer.memberId() != memberId) {
            throw new CustomerAccessDeniedException();
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
        BigDecimal totalAmount = supplyAmount.add(vatAmount);
        long quoteId = insertQuote(memberId, customer.id(), supplyAmount, vatAmount, totalAmount, request);
        quoteRepository.insertItems(quoteId, lines);
        return quoteRepository.findById(quoteId)
                .orElseThrow(() -> new IllegalStateException("quote was not stored"));
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
}
