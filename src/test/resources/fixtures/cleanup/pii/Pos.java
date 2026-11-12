package fixture;

/** 설계 64 — 코드 정리 pii(카드번호 가리기) */
class Pos {

    // 예: 1111-2222-3333-4444 — 주석 속 번호는 지적이 아니라 그대로
    static final String CARD = "1234-5678-9012-3456";

    String pair() {
        return "9876-5432-1098-7654" + " / " + "1111-0000-2222-0003";
    }

    String notCard() {
        return "1234-5678-9012-34567";
    }
}
