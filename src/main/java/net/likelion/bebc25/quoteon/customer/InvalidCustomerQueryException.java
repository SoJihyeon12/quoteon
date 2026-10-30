package net.likelion.bebc25.quoteon.customer;

public class InvalidCustomerQueryException extends RuntimeException {

    public InvalidCustomerQueryException(String message) {
        super(message);
    }
}
