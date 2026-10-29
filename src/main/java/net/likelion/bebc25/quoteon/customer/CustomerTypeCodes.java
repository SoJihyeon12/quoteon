package net.likelion.bebc25.quoteon.customer;

public final class CustomerTypeCodes {

    public static final String INDIVIDUAL = "개인";
    public static final String CORPORATE = "기업";
    public static final String INVALID_MESSAGE = "고객 유형은 개인 또는 기업만 선택할 수 있습니다.";

    private CustomerTypeCodes() {
    }

    public static boolean isAllowed(String value) {
        return INDIVIDUAL.equals(value) || CORPORATE.equals(value);
    }
}
