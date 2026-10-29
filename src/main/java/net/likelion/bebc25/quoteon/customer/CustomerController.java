package net.likelion.bebc25.quoteon.customer;

import jakarta.validation.Valid;
import net.likelion.bebc25.quoteon.customer.dto.CreateCustomerRequest;
import net.likelion.bebc25.quoteon.customer.dto.CustomerPageResponse;
import net.likelion.bebc25.quoteon.customer.dto.CustomerResponse;
import net.likelion.bebc25.quoteon.security.MemberPrincipal;
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

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @PostMapping
    public ResponseEntity<CustomerResponse> create(@Valid @RequestBody CreateCustomerRequest request) {
        CustomerResponse response = customerService.create(currentMemberId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public CustomerResponse update(
            @PathVariable long id,
            @Valid @RequestBody CreateCustomerRequest request
    ) {
        return customerService.update(currentMemberId(), id, request);
    }

    @GetMapping("/{id}")
    public CustomerResponse detail(@PathVariable long id) {
        return customerService.get(currentMemberId(), id);
    }

    @GetMapping
    public CustomerPageResponse list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String customerType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return customerService.list(currentMemberId(), keyword, customerType, page, size);
    }

    private long currentMemberId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        MemberPrincipal principal = (MemberPrincipal) authentication.getPrincipal();
        return principal.id();
    }
}
