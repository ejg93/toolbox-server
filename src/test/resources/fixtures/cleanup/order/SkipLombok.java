package cleanup.order;

import lombok.Data;

/** 5-34b — Lombok 은 필드 순서가 생성자 인자 순서다 */
@Data
public class SkipLombok {
    private String a;
    public String b;
}
