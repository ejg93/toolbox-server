package kr.ejg.fx.generic.cmm;

import org.springframework.beans.factory.annotation.Autowired;

public abstract class CrudService<D extends CrudDao<T>, T> {
    @Autowired
    protected D dao;

    public T get(String id) {
        return dao.get(id);
    }

    public int save(T e) {
        return dao.insert(e);
    }

    /** zheng BaseServiceImpl 꼴 — 매퍼의 Object 메서드(리플렉션)는 문장이 아니다(R-11) */
    public String name() {
        return dao.getClass().getName();
    }
}
