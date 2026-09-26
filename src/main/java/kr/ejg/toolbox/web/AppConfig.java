package kr.ejg.toolbox.web;

import java.nio.file.Path;

/**
 * 서버 기동 설정. 바인드 주소는 여기 없다 — {@link App#HOST} 상수다(절대 규칙 1).
 *
 * @param port        시작 포트. 점유 중이면 +1 씩 {@link App#PORT_TRIES} 회. 0 이면 OS 가 고른다
 * @param profileName 활성 프로필 이름. 없으면 null
 * @param dataDir     {@code data/} 경로
 * @param openBrowser 기동 뒤 기본 브라우저를 열지
 */
public record AppConfig(int port, String profileName, Path dataDir, boolean openBrowser) {

    public static final int DEFAULT_PORT = 41780;

    public static AppConfig defaults() {
        return new AppConfig(DEFAULT_PORT, null, Path.of("data"), true);
    }
}
