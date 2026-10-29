package net.likelion.bebc25.quoteon.common;

public record ApiErrorResponse(
        int status,
        String code,
        String message,
        String memberStatus,
        String name,
        String email,
        String companyName
) {

    public static ApiErrorResponse of(int status, String code, String message) {
        return new ApiErrorResponse(status, code, message, null, null, null, null);
    }
}
