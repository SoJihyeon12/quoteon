package net.likelion.bebc25.quoteon.member;

import java.util.Arrays;
import java.util.Optional;

public enum MemberDeactivationReason {
    RESIGNED("퇴사 처리"),
    LEAVE("휴직"),
    CONTRACT_ENDED("계약 종료"),
    ACCOUNT_SUSPENDED("계정 사용 중단"),
    OTHER("기타");

    private final String label;

    MemberDeactivationReason(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static Optional<MemberDeactivationReason> fromCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(reason -> reason.name().equals(code.trim()))
                .findFirst();
    }

    public static String formatStoredReason(String code, String detail) {
        MemberDeactivationReason reason = fromCode(code)
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 비활성화 사유입니다."));
        if (reason != OTHER) {
            return reason.label();
        }
        String trimmed = detail == null ? "" : detail.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("기타 사유를 입력해 주세요.");
        }
        if (trimmed.length() > 200) {
            throw new IllegalArgumentException("기타 사유는 200자 이하로 입력해 주세요.");
        }
        return reason.label() + ": " + trimmed;
    }
}
