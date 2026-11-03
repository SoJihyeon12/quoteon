package net.likelion.bebc25.quoteon.quote;

/**
 * 와이어프레임 정식 견적서(2.16) / 관리자 상세(2.38) 그림에 그려진 공급자 예시.
 * 회원 소속이나 저장된 공급자 정보가 아니며, 견적서 표시용이다.
 */
final class WireframeSupplierExample {

    private WireframeSupplierExample() {
    }

    static final String MANUFACTURER = "에이원퍼니처";

    static final String VALIDITY_NOTICE = "본 견적서는 견적일로부터 30일간 유효합니다.";

    static final String INTRO = "아래와 같이 견적합니다. (VAT 별도)";

    static final String[][] FIELDS = {
            {"상호", "에이원퍼니처 (A1 Furniture)"},
            {"대표자", "김현진"},
            {"주소", "서울특별시 강남구 테헤란로 123"},
            {"업태/업종", "도소매업/가구 및 가구용품 판매업"},
            {"전화", "02-1234-1234"}
    };
}
