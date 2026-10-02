package egov.neg.service.impl;

import egovframework.rte.fdl.cmmn.EgovAbstractServiceImpl;
import egov.neg.service.UserService;
import org.springframework.stereotype.Service;

@Service("userService")
public class UserServiceImpl extends EgovAbstractServiceImpl implements UserService {
    public void a() throws Exception {
        try { b(); } catch (IllegalStateException e) { throw processException("fail", e); }
        try { b(); } catch (IllegalArgumentException e) { egovLogger.error("x", e); }
        try { b(); } catch (RuntimeException e) { LOGGER.warn("x"); return; }
    }

    void b() {
    }
}
