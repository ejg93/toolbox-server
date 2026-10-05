package kr.ejg.toolbox;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 0-49 — 시험 JVM 은 surefire {@code argLine} 의 {@code -Xmx3g} 로 돈다. 지워지면 JDK 기본(RAM 1/4, 32GB PC 에서 8GB)까지 자라
 * {@code verify.sh --full} 이 Docker VM 과 겹쳐 메모리 부족으로 끊긴다(2026-10-05 실측 java 5.2GB · 남은 메모리 0.9GB)
 */
class HeapCapTest {

    @Test
    void surefireCapsHeap() {
        long max = Runtime.getRuntime().maxMemory();
        assertTrue(max <= 3_500L * 1024 * 1024,
                "시험 JVM 최대 힙 " + (max >> 20) + "MB — pom.xml surefire argLine 의 -Xmx3g 가 빠졌다");
    }
}
