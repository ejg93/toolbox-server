package kr.ejg.toolbox.cli;

import java.util.concurrent.Callable;
import picocli.CommandLine.Mixin;

/** 배치 명령 바탕(8-3) — 서버를 열고 본문을 돌린 뒤 꼭 닫는다. 끝 코드는 {@link Batch} 머리 주석 */
abstract class BatchCommand implements Callable<Integer> {

    @Mixin
    Batch batch;

    /** 프로필이 꼭 있어야 하나 — 없으면 끝 코드 2 */
    boolean needsProfile() {
        return true;
    }

    abstract int body() throws Exception;

    @Override
    public final Integer call() throws Exception {
        try {
            batch.open(needsProfile());
            return body();
        } catch (Batch.Exit e) {
            return e.code;
        } finally {
            batch.close();
            batch.out().flush();
            batch.err().flush();
        }
    }
}
