package sample.service.impl;

import java.util.List;
import javax.annotation.Resource;
import org.springframework.stereotype.Service;
import sample.service.BoardService;
import sample.service.BoardVO;

@Service("BoardServiceTest")
public class BoardServiceTestImpl implements BoardService {

	@Resource(name = "BoardDAO")
	private BoardDAO boardDAO;

	public List<BoardVO> selectList(BoardVO vo) throws Exception {
		return boardDAO.selectTestList(vo);
	}

	public void insert(BoardVO vo) {
	}

	public BoardVO selectDetail(BoardVO vo) {
		return null;
	}
}
