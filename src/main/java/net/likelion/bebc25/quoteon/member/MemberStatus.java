package net.likelion.bebc25.quoteon.member;

public final class MemberStatus {

    public static final String PENDING = "PENDING";
    public static final String ACTIVE = "ACTIVE";
    public static final String REJECTED = "REJECTED";
    public static final String INACTIVE = "INACTIVE";

    private MemberStatus() {
    }

    public static String message(String status) {
        return switch (status) {
            case PENDING -> "관리자 승인 대기 중입니다.";
            case REJECTED -> "가입 신청이 거절되었습니다.";
            case INACTIVE -> "계정이 비활성화되어 서비스를 이용할 수 없습니다.";
            default -> "서비스를 이용할 수 없습니다.";
        };
    }

    public static boolean isRejectedOrInactive(String status) {
        return REJECTED.equals(status) || INACTIVE.equals(status);
    }
}
