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
}
