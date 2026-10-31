package kr.ejg.fx.generic.cmm;

import java.util.List;

public abstract class TreeService<D extends TreeDao<T>, T> extends CrudService<D, T> {
    public List<T> tree(T e) {
        return dao.findTree(e);
    }
}
