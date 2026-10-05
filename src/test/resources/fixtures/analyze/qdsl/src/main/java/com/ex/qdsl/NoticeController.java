package com.ex.qdsl;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 6-17 — 같은 이름 엔티티를 Q import 로 고르는 프로그램 */
@RestController
public class NoticeController {

    private final NoticeQueryDao noticeQueryDao;

    public NoticeController(NoticeQueryDao noticeQueryDao) {
        this.noticeQueryDao = noticeQueryDao;
    }

    @GetMapping("/q/notices")
    public Object notices() {
        return noticeQueryDao.notices();
    }
}
