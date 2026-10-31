package kr.ejg.fx.generic.cmm;

import java.util.List;

public interface TreeDao<T> extends CrudDao<T> {
    List<T> findTree(T e);
}
