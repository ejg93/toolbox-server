package egov.pos.service.impl;

import org.springframework.stereotype.Service;

@Service("wrongName")
public class UserServiceImpl implements OtherService {
    public void a() {
        try { b(); } catch (Exception e) { }
        try {
            b();
        } catch (Exception e) {
            // 무시
        }
        try { b(); } catch (Exception e) { int x = 1; }
    }

    void b() throws Exception {
    }
}
