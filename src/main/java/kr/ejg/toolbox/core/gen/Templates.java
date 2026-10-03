package kr.ejg.toolbox.core.gen;

import freemarker.cache.FileTemplateLoader;
import freemarker.cache.MultiTemplateLoader;
import freemarker.cache.TemplateLoader;
import freemarker.core.TemplateClassResolver;
import freemarker.template.Configuration;
import freemarker.template.SimpleObjectWrapper;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import freemarker.template.TemplateNotFoundException;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.Map;

/**
 * 템플릿 세트 하나를 그린다(7-1). 템플릿은 사업 프로필 쪽 사용자 파일이라 잠근다 — 로더는 세트 폴더(와 부모 세트 폴더)뿐이라 그 밖
 * include 불가, {@code ?new} 는 어떤 클래스도 못 만들고 {@code ?api} 는 꺼짐, 모델은 Map·List·문자열·수·참거짓만(자바 빈 안 넘김).
 * 보간은 대괄호 {@code [=이름]} — 템플릿 안 {@code ${…}}(JSP EL)·{@code #{…}}(MyBatis)는 글자 그대로 나간다. 자동 이스케이프 없음(코드 생성).
 * 잠금 설정은 그리기와 같은 메서드에 둔다(정적 분석이 메서드 안만 본다 — 6-2 SafeSax 와 같은 까닭).
 */
public final class Templates {

    private final TemplateSet set;

    public Templates(TemplateSet set) {
        this.set = set;
    }

    public TemplateSet set() {
        return set;
    }

    /** 세트 폴더의 템플릿 하나 */
    public String render(String templateName, Map<String, Object> model) throws IOException {
        return process(templateName, null, model);
    }

    /** 출력 경로 식 — 같은 설정의 문자열 템플릿 */
    public String renderPath(String pathExpr, Map<String, Object> model) throws IOException {
        return process("path", pathExpr, model);
    }

    private String process(String name, String inline, Map<String, Object> model) throws IOException {
        Configuration cfg = new Configuration(Configuration.VERSION_2_3_35);
        TemplateLoader[] loaders = new TemplateLoader[set.dirs().size()];
        for (int i = 0; i < loaders.length; i++) {
            Path d = set.dirs().get(i);
            loaders[i] = new FileTemplateLoader(d.toFile());
        }
        cfg.setTemplateLoader(new MultiTemplateLoader(loaders));
        cfg.setNewBuiltinClassResolver(TemplateClassResolver.ALLOWS_NOTHING_RESOLVER);
        cfg.setAPIBuiltinEnabled(false);
        cfg.setObjectWrapper(new SimpleObjectWrapper(Configuration.VERSION_2_3_35));
        cfg.setDefaultEncoding("UTF-8");
        cfg.setLocalizedLookup(false);
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        cfg.setLogTemplateExceptions(false);
        cfg.setWrapUncheckedExceptions(true);
        cfg.setAutoEscapingPolicy(Configuration.DISABLE_AUTO_ESCAPING_POLICY);
        cfg.setInterpolationSyntax(Configuration.SQUARE_BRACKET_INTERPOLATION_SYNTAX);
        Template t;
        try {
            t = inline == null ? cfg.getTemplate(name) : new Template(name, new StringReader(inline), cfg);
        } catch (TemplateNotFoundException e) {
            throw new IOException("템플릿이 없다: " + set.name() + "/" + name, e);
        }
        StringWriter out = new StringWriter();
        try {
            t.process(model, out);
        } catch (TemplateException e) {
            throw new IOException(set.name() + "/" + name + " 그리기 실패 — " + e.getMessageWithoutStackTop(), e);
        }
        return out.toString();
    }
}
