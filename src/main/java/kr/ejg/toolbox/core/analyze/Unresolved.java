package kr.ejg.toolbox.core.analyze;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 프로그램 분석이 못 풀었거나 덜 푼 자리(6-2·6-3) — 지우지 않고 낸다. detail 은 식별자(ns.id·클래스.메서드·표·refid)만, 코드 본문은 없다(규칙 3).
 * kind 는 {@link #KINDS} 의 키만 — 모르는 종류는 생성자가 거절한다(6-22, 새 종류가 뜻 없이 못 들어온다)
 */
public record Unresolved(String kind, String file, int line, String detail) {

    /** 종류 하나의 글 — 한글 이름 · 뜻 한 줄 · 푸는 법 한 줄(6-22). 화면 칩·xlsx 미해결 시트가 같은 글을 쓴다 */
    public record Kind(String name, String meaning, String fix) {
    }

    /** 영문 코드 → 글. 넣은 순서가 화면 칩 순서. 코드는 H2 analyze_unresolved.kind 와 corpus baseline 이 쥐므로 바꾸지 않는다 */
    public static final Map<String, Kind> KINDS = kinds();

    public Unresolved {
        if (!KINDS.containsKey(kind)) {
            throw new IllegalArgumentException("모르는 미해결 종류: " + kind + " — Unresolved.KINDS 에 이름·뜻·푸는 법을 더한다");
        }
    }

    private static Map<String, Kind> kinds() {
        Map<String, Kind> m = new LinkedHashMap<>();
        m.put("parse", new Kind("파싱 실패", "Java 또는 매퍼 XML 을 파서가 못 읽어 그 파일을 통째로 건너뛰었다(줄은 첫 오류 자리)",
                "그 파일이 컴파일·XML 검증을 통과하는지 본다"));
        m.put("table", new Kind("표 자리 식별자 아님", "SQL 의 FROM·JOIN·INTO 뒤가 표 이름이 아니라 변수·${}·괄호라 표를 못 읽었다",
                "동적 표 이름이면 그 문장의 CRUD 는 손으로 적는다"));
        m.put("tagVerb", new Kind("태그와 동사 다름", "매퍼 태그(select 등)와 SQL 첫 동사가 어긋난다 — CRUD 는 SQL 동사를 따랐다", "태그를 SQL 에 맞춘다"));
        m.put("dialect", new Kind("파일마다 다른 표", "같은 ns.id 문장이 여러 매퍼 파일(방언별)에 있는데 쓰는 표가 다르다 — 합집합으로 셌다",
                "방언 파일끼리 표 목록을 맞춘다"));
        m.put("missing", new Kind("문장·조각 없음", "코드가 부르는 ns.id(또는 include refid)가 매퍼 색인에 없다. 끝 * 는 접두 호출",
                "매퍼 파일이 분석 폴더 안에 있는지, 네임스페이스·id 오타를 본다"));
        m.put("statement", new Kind("문장 id 못 읽음", "DAO 호출의 문장 id(또는 createQuery 의 JPQL)가 문자열이 아니라 변수·연산이라 어느 문장인지 모른다",
                "문자열 상수로 바꾸거나 그 메서드의 CRUD 는 손으로 적는다"));
        m.put("prefix", new Kind("접두 호출", "문장 id 가 \"Login.update\" + x 꼴이라 앞부분이 같은 문장을 전부 후보로 넣었다",
                "실제 쓰는 문장만 남기려면 호출을 상수로 나눈다"));
        m.put("ambiguous", new Kind("후보 여럿", "필드 타입·저장소·엔티티 이름이 여러 클래스에 맞아 하나를 못 골랐다(모듈마다 같은 이름 서비스 등)",
                "같은 이름 클래스를 패키지로 구분하거나 결과를 손으로 가른다"));
        m.put("depth", new Kind("호출 깊이 상한", "컨트롤러에서 DAO 까지 호출 사슬이 상한을 넘어 그 아래는 안 따라갔다", "사슬이 긴 메서드는 손으로 보탠다"));
        m.put("viewDynamic", new Kind("뷰 이름 동적", "반환하는 뷰 이름이 변수·연산이라 어느 JSP 인지 모른다", "뷰 이름을 문자열로 두거나 그 프로그램의 JSP 는 손으로 잇는다"));
        m.put("jspUrl", new Kind("JSP 링크에 EL", "JSP 링크 URL 에 ${} 가 있거나 / 로 시작하지 않아 어느 프로그램을 부르는지 모른다(${} 자리는 비웠다)",
                "영향도의 JSP 목록이 덜 나올 수 있다 — 상수 URL 이면 그대로 쓴다"));
        m.put("entityName", new Kind("엔티티 표 이름 추정", "@Table(name) 이 없어 클래스 이름을 snake_case 로 바꿔 표 이름으로 삼았다",
                "실제 표 이름이 다르면 @Table(name=…) 을 적는다"));
        m.put("jpaType", new Kind("저장소 엔티티 모름", "저장소 인터페이스의 엔티티 타입을 못 읽었다(타입 인자 없음·엔티티 색인에 없음)",
                "JpaRepository<엔티티, ID> 꼴로 적거나 엔티티가 분석 폴더에 있는지 본다"));
        m.put("jpaMethod", new Kind("저장소 메서드 CRUD 모름", "파생 메서드 이름이 find·save·delete… 규칙에 안 맞아 CRUD 를 못 정했다", "그 메서드의 CRUD 는 손으로 적는다"));
        m.put("jpql", new Kind("JPQL 해석 실패", "@Query·createQuery 의 JPQL 을 못 읽었거나(상수 아님) JPQL 의 엔티티 이름이 색인에 없다",
                "JPQL 을 상수로 두고 엔티티 이름을 맞춘다"));
        m.put("namedQuery", new Kind("NamedQuery", "createNamedQuery 는 이름만 있어 본문을 안 따라갔다", "그 쿼리의 표는 손으로 적는다"));
        m.put("criteria", new Kind("Criteria API", "getCriteriaBuilder 로 짠 쿼리는 표를 못 읽는다", "그 메서드의 표는 손으로 적는다"));
        m.put("querydsl", new Kind("QueryDSL Q타입 모름", "Q클래스가 가리키는 엔티티를 색인에서 못 찾았다", "Q 클래스 이름과 엔티티 이름을 맞춘다"));
        return Collections.unmodifiableMap(m);
    }
}
