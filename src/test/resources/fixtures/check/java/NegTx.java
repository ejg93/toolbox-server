package egov.neg;

import org.springframework.transaction.annotation.Transactional;

/** 설계 49 — TxAst 음성: rollbackFor·readOnly·JTA rollbackOn · 같은 속성 호출자 · 다른 빈 호출 · 재귀 · 인터페이스 */
public class NegTx {
    private final NegTx bean;

    public NegTx(NegTx bean) {
        this.bean = bean;
    }

    @Transactional(rollbackFor = Exception.class)
    public void a() {
        work();
    }

    @Transactional(readOnly = true)
    public void r() {
        work();
    }

    @Transactional(rollbackForClassName = "Exception")
    public void n() {
        work();
    }

    @Transactional(rollbackOn = Exception.class)
    public void j() {
        work();
    }

    @Transactional(rollbackFor = Exception.class)
    public void caller() {
        a();
        Runnable t = () -> a();
        t.run();
    }

    public void other() {
        helper();
    }

    public void viaBean() {
        bean.a();
    }

    @Transactional(rollbackFor = Exception.class)
    public void rec() {
        if (ready()) {
            rec();
        }
    }

    boolean ready() {
        return bean != null;
    }

    void helper() {
        work();
    }

    void work() {
        Thread.yield();
    }
}

interface NegTxApi {
    @Transactional(rollbackFor = Exception.class)
    void save();
}
