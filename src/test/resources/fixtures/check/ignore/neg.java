/**
 * 수정일 수정자 수정내용
 * 5-29 억제 표식 — 표식이 먹으면 0 건
 */
package a;

public class Neg {
    void run(Exception e) {
        System.out.println("x"); // tb-ignore common.sysout 콘솔 유틸
        // tb-ignore-next
        // TODO 지운다
        e.printStackTrace(); // TODO x  // tb-ignore common.printStackTrace,common.todo
    }
}
