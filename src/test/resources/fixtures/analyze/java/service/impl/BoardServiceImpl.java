package sample.service.impl;

import java.util.List;
import javax.annotation.Resource;
import org.springframework.stereotype.Service;
import sample.service.BoardService;
import sample.service.BoardVO;

@Service("BoardService")
public class BoardServiceImpl extends EgovAbstractServiceImpl implements BoardService {

	@Resource(name = "BoardDAO")
	private BoardDAO boardDAO;

	@Override
	public List<BoardVO> selectList(BoardVO vo) throws Exception {
		return boardDAO.selectBoardList(vo);
	}

	@Override
	public void insert(BoardVO vo) throws Exception {
		boardDAO.insertBoard(vo);
		boardDAO.updateIncorrect(null);
	}

	@Override
	public BoardVO selectDetail(BoardVO vo) throws Exception {
		ping(vo);
		return boardDAO.selectDetail(vo);
	}

	private void ping(BoardVO vo) throws Exception {
		pong(vo);
		boardDAO.selectByVar(vo);
		boardDAO.selectDynamic(vo);
	}

	private void pong(BoardVO vo) throws Exception {
		ping(vo);
	}
}
