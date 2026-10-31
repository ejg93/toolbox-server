package kr.ejg.fx.generic.cmm;

public interface CrudDao<T> {
    T get(String id);

    int insert(T e);
}
