package kr.ejg.fx.generic.service;

import kr.ejg.fx.generic.cmm.TreeService;
import kr.ejg.fx.generic.dao.OfficeDAO;
import kr.ejg.fx.generic.entity.Office;
import org.springframework.stereotype.Service;

@Service
public class OfficeService extends TreeService<OfficeDAO, Office> {
}
