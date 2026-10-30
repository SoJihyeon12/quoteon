package net.likelion.bebc25.quoteon.customer;

import net.likelion.bebc25.quoteon.customer.dto.CustomerResponse;

record CustomerDetail(
        long memberId,
        CustomerResponse customer
) {
}
