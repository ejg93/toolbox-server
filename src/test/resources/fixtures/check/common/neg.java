/**
 * 수정일 수정자 수정내용
 * 주석 안의 System.out.println("x") · localhost 는 안 걸린다
 */
package a;

public class Neg {
    void run(org.slf4j.Logger log, Exception e) {
        // System.out.println("x"); e.printStackTrace(); 192.168.0.1
        log.info("todo list");
        e.printStackTrace(System.err);
        String v = "1.192.168.0.10";
        String host = "localhostname";
        String ver = "10.0.1";
        // 생성 결과에 TODO 문구를 남긴다 — 설명 글 속 낱말은 표시가 아니다(5-44a)
        String note = "TODO 타입 확인: " + ver;
    }
}
