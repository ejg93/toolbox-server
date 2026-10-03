package sample.service;

import java.util.List;

public interface BoardService {
	List<BoardVO> selectList(BoardVO vo) throws Exception;
	void insert(BoardVO vo) throws Exception;
	BoardVO selectDetail(BoardVO vo) throws Exception;
}
