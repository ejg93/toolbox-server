package egov.pos;

import org.springframework.transaction.annotation.Transactional;

/** 설계 49 — TxAst 양성: rollbackFor 없음 · private·static 의 tx · 같은 클래스 tx 메서드 직접 호출(속성 다른 호출자 포함) */
public class PosTx {

    @Transactional
    public void a() {
        work();
    }

    @Transactional(readOnly = false)
    public void b() {
        work();
    }

    @Transactional(noRollbackFor = IllegalStateException.class)
    public void c() {
        work();
    }

    public void caller() {
        a();
        this.b();
    }

    @Transactional(rollbackFor = Exception.class)
    public void caller2() {
        c();
    }

    @Transactional(rollbackFor = Exception.class)
    static void e() {
        Thread.yield();
    }

    void work() {
        Thread.yield();
    }

    @Transactional(rollbackFor = Exception.class)
    private void d() {
        work();
    }
}

@Transactional
class PosTxHolder {

    public void x() {
        y();
    }

    @Transactional(readOnly = true)
    public void y() {
        Thread.yield();
    }
}
