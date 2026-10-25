package com.ex.jdbc;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/** 음성 — 이름이 같은 execute 지만 필드 타입이 JDBC 가 아니다(문장도 미해결도 없어야) */
@Component
public class RemoteGateway {

    private final RestTemplate rest = new RestTemplate();

    public Object call() {
        return rest.execute("http://remote.invalid/x", null, null, null);
    }
}
