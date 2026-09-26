package kr.ejg.toolbox.cli;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import kr.ejg.toolbox.core.deliverable.Forms;
import kr.ejg.toolbox.core.report.Mapping;
import kr.ejg.toolbox.core.report.XlsxFiller;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/** 2-4 — 예시 양식 11장과 예시 매핑 YAML 을 값 표 열로 만든다. 손으로 만들지 않아 열이 어긋나지 않는다 */
@Command(name = "make-templates", description = "산출물 예시 양식(xlsx 11)과 예시 매핑 YAML 을 만든다.")
public final class MakeTemplates implements Callable<Integer> {

    @Option(names = "--out", defaultValue = "templates/deliverable/example", description = "양식 폴더")
    Path out;

    @Option(names = "--mapping", defaultValue = "mappings/deliverable/example.yaml", description = "매핑 YAML")
    Path mapping;

    @Spec
    CommandSpec spec;

    /** 예시 양식의 머리·첫 데이터 행(엑셀 행 번호) */
    public static final int HEADER_ROW = 2;
    public static final int FIRST_ROW = 3;

    @Override
    public Integer call() throws Exception {
        for (Forms.Form f : Forms.all()) {
            XlsxFiller.writeTemplate(f, out.resolve(f.file()));
        }
        Path dir = mapping.toAbsolutePath().getParent();
        if (dir != null) {
            Files.createDirectories(dir);
        }
        Files.writeString(mapping, Mapping.exampleYaml(Forms.all(), HEADER_ROW, FIRST_ROW), StandardCharsets.UTF_8);
        spec.commandLine().getOut().println("양식 " + Forms.all().size() + "장 → " + out + ", 매핑 → " + mapping);
        spec.commandLine().getOut().flush();
        return 0;
    }
}
