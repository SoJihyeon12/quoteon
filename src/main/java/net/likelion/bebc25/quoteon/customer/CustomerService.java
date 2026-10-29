package net.likelion.bebc25.quoteon.customer;

import net.likelion.bebc25.quoteon.customer.dto.CreateCustomerRequest;
import net.likelion.bebc25.quoteon.customer.dto.CustomerPageResponse;
import net.likelion.bebc25.quoteon.customer.dto.CustomerResponse;
import net.likelion.bebc25.quoteon.customer.dto.CustomerSummaryResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CustomerService {

    private static final int DEFAULT_PAGE = 0;
    private static final int MAX_PAGE_SIZE = 100;

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    public CustomerResponse create(long memberId, CreateCustomerRequest request) {
        long id = customerRepository.insert(
                memberId,
                request.name(),
                request.customerType(),
                request.phone(),
                request.email(),
                request.address(),
                request.memo()
        );
        return new CustomerResponse(
                id,
                request.name(),
                request.customerType(),
                request.phone(),
                request.email(),
                request.address(),
                request.memo()
        );
    }

    public CustomerResponse get(long memberId, long customerId) {
        CustomerDetail detail = customerRepository.findDetailById(customerId)
                .orElseThrow(CustomerNotFoundException::new);
        if (detail.memberId() != memberId) {
            throw new CustomerAccessDeniedException();
        }
        return detail.customer();
    }

    public CustomerPageResponse list(long memberId, String keyword, int page, int size) {
        if (page < DEFAULT_PAGE) {
            throw new InvalidCustomerQueryException("페이지 번호는 0 이상이어야 합니다.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidCustomerQueryException("페이지 크기는 1 이상 100 이하여야 합니다.");
        }
        String normalizedKeyword = normalizeKeyword(keyword);
        long totalElements = customerRepository.countSelectable(memberId, normalizedKeyword);
        int totalPages = totalElements == 0 ? 0 : (int) ((totalElements + size - 1) / size);
        List<CustomerSummaryResponse> items = customerRepository.findSelectable(
                memberId,
                normalizedKeyword,
                size,
                (long) page * size
        );
        return new CustomerPageResponse(items, page, size, totalElements, totalPages);
    }

    private static String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        String trimmed = keyword.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
