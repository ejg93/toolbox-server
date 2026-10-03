package sample.service.impl;

import org.springframework.stereotype.Service;
import sample.mapper.SampleMapper;
import sample.service.OtherService;

@Service("otherService")
public class OtherServiceImpl implements OtherService {

	private final SampleMapper sampleMapper;
	private final SqlSessionTemplate sqlSession;

	public OtherServiceImpl(SampleMapper sampleMapper, SqlSessionTemplate sqlSession) {
		this.sampleMapper = sampleMapper;
		this.sqlSession = sqlSession;
	}

	public void run() {
		sampleMapper.selectSample(1);
		sqlSession.selectList("Other.fromTemplate");
	}
}
