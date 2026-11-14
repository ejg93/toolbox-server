package kr.ejg.toolbox.web;

import io.javalin.http.BadRequestResponse;
import io.javalin.json.JavalinJackson;
import io.javalin.json.JsonMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.List;
import java.util.stream.Stream;

/**
 * 0-91(설계 69) — 본문 JSON 이 null 이거나 요청 레코드의 목록 성분(첫 층)에 null 원소가 있으면 400. 나머지는 {@link JavalinJackson} 그대로.
 * 라우트마다 null 검사를 두지 않는다 — 한 자리
 */
final class StrictJson implements JsonMapper {

    private final JavalinJackson inner;

    StrictJson(JavalinJackson inner) {
        this.inner = inner;
    }

    @Override
    public String toJsonString(Object obj, Type type) {
        return inner.toJsonString(obj, type);
    }

    @Override
    public InputStream toJsonStream(Object obj, Type type) {
        return inner.toJsonStream(obj, type);
    }

    @Override
    public void writeToOutputStream(Stream<?> stream, OutputStream out) {
        inner.writeToOutputStream(stream, out);
    }

    /** inner.fromJsonString 은 Kotlin 이라 결과 null 에 NPE — 매퍼로 직접 읽는다. Jackson 예외는 그대로(App.jsonErrors 가 400) */
    @Override
    public <T> T fromJsonString(String json, Type type) {
        try {
            return checked(inner.getMapper().readValue(json, inner.getMapper().getTypeFactory().constructType(type)));
        } catch (IOException e) {
            throw StrictJson.<RuntimeException>sneaky(e);
        }
    }

    @Override
    public <T> T fromJsonStream(InputStream json, Type type) {
        try {
            return checked(inner.getMapper().readValue(json, inner.getMapper().getTypeFactory().constructType(type)));
        } catch (IOException e) {
            throw StrictJson.<RuntimeException>sneaky(e);
        }
    }

    /** 검사 예외를 감싸지 않고 던진다 — JsonProcessingException 이 App.jsonErrors 의 매퍼에 그대로 닿게 */
    @SuppressWarnings("unchecked")
    private static <E extends Throwable> E sneaky(Throwable e) throws E {
        throw (E) e;
    }

    static <T> T checked(T body) {
        if (body == null) {
            throw new BadRequestResponse("본문이 비었다");
        }
        if (body.getClass().isRecord()) {
            for (RecordComponent rc : body.getClass().getRecordComponents()) {
                if (value(rc, body) instanceof List<?> l && hasNull(l)) {
                    throw new BadRequestResponse("목록 " + rc.getName() + " 안에 null 이 있다");
                }
            }
        }
        return body;
    }

    /** 성분 값 — 못 읽으면 null(검사 건너뜀) */
    private static Object value(RecordComponent rc, Object body) {
        try {
            return rc.getAccessor().invoke(body);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /** List.contains(null) 은 불변 목록에서 던진다 — 하나씩 */
    private static boolean hasNull(List<?> l) {
        for (Object o : l) {
            if (o == null) {
                return true;
            }
        }
        return false;
    }
}
