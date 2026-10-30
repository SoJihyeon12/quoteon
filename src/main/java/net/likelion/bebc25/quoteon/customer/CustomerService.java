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
        return requireOwned(memberId, customerId).customer();
    }

    public CustomerResponse deactivate(long memberId, long customerId) {
        CustomerDetail detail = requireOwned(memberId, customerId);
        int updated = customerRepository.deactivate(customerId, memberId);
        if (updated != 1) {
            throw new CustomerAlreadyInactiveException();
        }
        return detail.customer();
    }

    public CustomerResponse update(long memberId, long customerId, CreateCustomerRequest request) {
        requireOwned(memberId, customerId);
        int updated = customerRepository.update(
                customerId,
                memberId,
                request.name(),
                request.customerType(),
                request.phone(),
                request.email(),
                request.address(),
                request.memo()
        );
        if (updated != 1) {
            throw new CustomerNotFoundException();
        }
        return new CustomerResponse(
                customerId,
                request.name(),
                request.customerType(),
                request.phone(),
                request.email(),
                request.address(),
                request.memo()
        );
    }

    public CustomerPageResponse list(long memberId, String keyword, String customerType, int page, int size) {
        if (page < DEFAULT_PAGE) {
            throw new InvalidCustomerQueryException("페이지 번호는 0 이상이어야 합니다.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidCustomerQueryException("페이지 크기는 1 이상 100 이하여야 합니다.");
        }
        String normalizedKeyword = normalizeKeyword(keyword);
        String normalizedType = normalizeCustomerType(customerType);
        long totalElements = customerRepository.countSelectable(memberId, normalizedKeyword, normalizedType);
        int totalPages = totalElements == 0 ? 0 : (int) ((totalElements + size - 1) / size);
        List<CustomerSummaryResponse> items = customerRepository.findSelectable(
                memberId,
                normalizedKeyword,
                normalizedType,
                size,
                (long) page * size
        );
        return new CustomerPageResponse(items, page, size, totalElements, totalPages);
    }

    private CustomerDetail requireOwned(long memberId, long customerId) {
        CustomerDetail detail = customerRepository.findDetailById(customerId)
                .orElseThrow(CustomerNotFoundException::new);
        if (detail.memberId() != memberId) {
            throw new CustomerAccessDeniedException();
        }
        return detail;
    }

    private static String normalizeCustomerType(String customerType) {
        if (customerType == null) {
            return null;
        }
        String trimmed = customerType.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (!CustomerTypeCodes.isAllowed(trimmed)) {
            throw new InvalidCustomerQueryException(CustomerTypeCodes.INVALID_MESSAGE);
        }
        return trimmed;
    }

    private static String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        String trimmed = keyword.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
