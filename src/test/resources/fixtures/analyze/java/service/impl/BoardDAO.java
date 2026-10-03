package sample.service.impl;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;
import sample.cmm.EgovComAbstractDAO;
import sample.service.BoardVO;

@Repository("BoardDAO")
public class BoardDAO extends EgovComAbstractDAO {

	private static final String TEST_ID = "Board.selectTestList";

	public List<BoardVO> selectBoardList(BoardVO vo) {
		return selectList("Board.selectList", vo);
	}

	public List<BoardVO> selectTestList(BoardVO vo) {
		return selectList(TEST_ID, vo);
	}

	public void insertBoard(BoardVO vo) {
		insert("Board.insert", vo);
	}

	public void updateIncorrect(Map<String, String> map) {
		update("Login.updateIncorrect" + map.get("SE"), map);
	}

	public BoardVO selectDetail(BoardVO vo) {
		return (BoardVO) selectOne("Board.selectDetail", vo);
	}

	public BoardVO selectByVar(BoardVO vo) {
		String queryId = "Board.selectVar";
		return (BoardVO) selectOne(queryId, vo);
	}

	public BoardVO selectDynamic(BoardVO vo) {
		return (BoardVO) selectOne(id(vo), vo);
	}

	private String id(BoardVO vo) {
		return "Board." + vo.getKind();
	}
}
