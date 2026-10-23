package kr.ejg.toolbox.core.check;

import edu.umd.cs.findbugs.BugCollectionBugReporter;
import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugPattern;
import edu.umd.cs.findbugs.DetectorFactoryCollection;
import edu.umd.cs.findbugs.FindBugs2;
import edu.umd.cs.findbugs.FindBugsProgress;
import edu.umd.cs.findbugs.Plugin;
import edu.umd.cs.findbugs.PluginException;
import edu.umd.cs.findbugs.Priorities;
import edu.umd.cs.findbugs.Project;
import edu.umd.cs.findbugs.SortedBugCollection;
import edu.umd.cs.findbugs.SourceLineAnnotation;
import edu.umd.cs.findbugs.config.UserPreferences;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import kr.ejg.toolbox.core.job.JobContext;

/**
 * 코드 검사 묶음 ㅅ 외부 분석기(5-38, 설계 34) — app.jar 안 SpotBugs 엔진에 사업의 컴파일된 클래스 폴더를 넣고, FindSecBugs 플러그인을 더해
 * 같은 결과 표(파일·줄·규칙·등급)의 {@link Finding} 으로 바꾼다. 외부 프로세스 없음(절대 규칙 1). 보고서 파일을 만들지 않는다 — 발췌는
 * 검출기의 짧은 설명과 메시지(클래스·메서드 이름)뿐이고 저장하지 않는다(규칙 3).
 * 규칙 {@code ext.spotbugs}·{@code ext.findsecbugs} 의 {@code params.minPriority}(1 높음·2 보통·3 낮음)·{@code params.exclude}(BUG_TYPE 글롭).
 * 우선순위 1·2·3 → 등급 error·warn·info. SpotBugs 정적 상태가 있어 한 번에 하나만 돈다
 */
public final class SpotBugsRun {

    public static final String SPOTBUGS = "ext.spotbugs";
    public static final String FINDSECBUGS = "ext.findsecbugs";
    static final String FINDSECBUGS_ID = "com.h3xstream.findsecbugs";
    private static final Object LOCK = new Object();
    /** 플러그인은 JVM 당 한 번 — 다시 읽으면 클래스로더가 쌓이고 Windows 에서 jar 가 잠긴다(5-38a) */
    private static Plugin findSecBugs;
    private static Set<String> findSecBugsTypes = Set.of();

    /** classes — 분석한 클래스 수, missingClasses — aux 에 없어 못 본 참조 수, noLine — 줄 정보가 없어 1 로 둔 지적 수, note — 못 돈 까닭(없으면 null) */
    public record Result(List<Finding> findings, int classes, int missingClasses, int noLine, String note) {

        public Result {
            findings = List.copyOf(findings);
        }
    }

    private SpotBugsRun() {
    }

    /**
     * @param sourceRoot 검사 폴더 — 지적의 소스 경로(패키지 경로 + 파일 이름)를 이 아래 상대 경로로 맞춘다
     * @param classesDir 컴파일된 클래스 폴더(WEB-INF/classes·target/classes)
     * @param libDir aux jar 폴더 — null 이면 classesDir 가 …/WEB-INF/classes 일 때 옆 lib
     * @param pluginJar FindSecBugs 플러그인 파일({@link Analyzers#findSecBugsJar}) — null 이면 코어만
     * @param defs 켜진 external 규칙 정의(둘 중 켜진 것)
     */
    public static Result run(Path sourceRoot, Path classesDir, Path libDir, Path pluginJar, List<Rule.Def> defs, JobContext ctx) {
        Rule.Def core = def(defs, SPOTBUGS);
        Rule.Def sec = def(defs, FINDSECBUGS);
        if (core == null && sec == null) {
            return new Result(List.of(), 0, 0, 0, null);
        }
        if (classesDir == null || !Files.isDirectory(classesDir)) {
            return new Result(List.of(), 0, 0, 0, "클래스 폴더가 없다 — " + classesDir);
        }
        int classes = countClasses(classesDir);
        if (classes == 0) {
            return new Result(List.of(), 0, 0, 0, "클래스 폴더에 .class 가 없다(빌드 전) — " + classesDir);
        }
        synchronized (LOCK) {
            Project project = new Project();
            project.addFile(classesDir.toString());
            Path lib = libDir != null ? libDir : webInfLib(classesDir);
            if (lib != null && Files.isDirectory(lib)) {
                try (Stream<Path> s = Files.list(lib)) {
                    s.filter(p -> p.getFileName().toString().endsWith(".jar")).sorted().forEach(p -> project.addAuxClasspathEntry(p.toString()));
                } catch (IOException e) {
                    return new Result(List.of(), classes, 0, 0, "lib 폴더를 못 읽었다 — " + e.getMessage());
                }
            }
            String note = null;
            if (sec != null && pluginJar != null) {
                try {
                    findSecBugs(pluginJar);
                } catch (PluginException | RuntimeException | LinkageError e) {
                    note = "FindSecBugs 플러그인을 못 읽었다 — " + e.getMessage();
                }
            }
            if (findSecBugs != null) {
                project.setPluginStatusTrinary(FINDSECBUGS_ID, sec != null);
            }
            FindBugs2 engine = new FindBugs2();
            BugCollectionBugReporter reporter = new BugCollectionBugReporter(project);
            reporter.setPriorityThreshold(Priorities.LOW_PRIORITY);
            engine.setProject(project);
            engine.setBugReporter(reporter);
            engine.setDetectorFactoryCollection(DetectorFactoryCollection.instance());
            engine.setUserPreferences(UserPreferences.createDefaultUserPreferences());
            engine.setProgressCallback(progress(ctx, classes));
            try {
                engine.execute();
            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                if (ctx != null) {
                    ctx.checkCancelled();
                }
                return new Result(List.of(), classes, 0, 0, "SpotBugs 가 멈췄다 — " + e.getMessage());
            } catch (JobContext.JobCancelledException e) {
                throw e;
            } catch (RuntimeException | LinkageError e) {
                // 엔진이 터져도 파일 규칙 결과는 남긴다 — 까닭만 note 로(마무리 리뷰)
                return new Result(List.of(), classes, 0, 0, "SpotBugs 가 멈췄다 — " + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
            if (ctx != null) {
                ctx.checkCancelled();
            }
            Map<String, List<String>> index = sourceIndex(sourceRoot);
            List<Finding> out = new ArrayList<>();
            int noLine = 0;
            for (BugInstance b : reporter.getBugCollection()) {
                boolean isSec = findSecBugsTypes.contains(b.getType());
                Rule.Def d = isSec ? sec : core;
                if (d == null || b.getPriority() > minPriority(d) || excluded(d, b.getType())) {
                    continue;
                }
                SourceLineAnnotation s = b.getPrimarySourceLineAnnotation();
                String file = mapFile(index, s.getSourcePath()); // 없으면 SpotBugs 가 클래스 단위 빈 주석을 준다(null 아님)
                int line = s.getStartLine() <= 0 ? 1 : s.getStartLine();
                noLine += s.getStartLine() <= 0 ? 1 : 0;
                String rule = (isSec ? "findsecbugs." : "spotbugs.") + b.getType();
                String excerpt = Finding.excerpt(b.getBugPattern().getShortDescription() + " — " + b.getMessageWithoutPrefix());
                out.add(new Finding(file, line, d.group(), rule.length() > 100 ? rule.substring(0, 100) : rule, severity(b.getPriority()), excerpt));
            }
            int missing = 0;
            if (reporter.getBugCollection() instanceof SortedBugCollection sc) {
                for (Iterator<String> it = sc.missingClassIterator(); it.hasNext(); it.next()) {
                    missing++;
                }
            }
            return new Result(out, classes, missing, noLine, note);
        }
    }

    private static Rule.Def def(List<Rule.Def> defs, String id) {
        return defs.stream().filter(d -> d.id().equals(id) && d.on()).findFirst().orElse(null);
    }

    /** FindSecBugs 플러그인 — JVM 에 한 번 읽어 둔다(같은 id 를 다른 파일에서 또 읽으면 SpotBugs 가 DuplicatePluginIdException). 처음 jar 가 이긴다 */
    static Plugin findSecBugs(Path jar) throws PluginException {
        synchronized (LOCK) {
            if (findSecBugs != null) {
                return findSecBugs;
            }
            Plugin p = Plugin.loadCustomPlugin(jar.toFile(), new Project());
            Set<String> types = new HashSet<>();
            for (BugPattern bp : p.getBugPatterns()) {
                types.add(bp.getType());
            }
            findSecBugsTypes = Set.copyOf(types);
            findSecBugs = p;
            return p;
        }
    }

    private static FindBugsProgress progress(JobContext ctx, int classes) {
        return new FindBugsProgress() {
            private int pass;
            private int done;

            @Override
            public void reportNumberOfArchives(int numArchivesToScan) {
                // 쓰지 않는다
            }

            @Override
            public void finishArchive() {
                // 쓰지 않는다
            }

            @Override
            public void startArchive(String name) {
                // 쓰지 않는다
            }

            @Override
            public void predictPassCount(int[] classesPerPass) {
                // 쓰지 않는다
            }

            @Override
            public void startAnalysis(int numClasses) {
                pass++;
                done = 0;
            }

            @Override
            public void finishClass() {
                done++;
                if (ctx != null) {
                    ctx.checkCancelled(); // 취소 — 엔진 안에서 던진다
                    if (done % 50 == 0) {
                        ctx.progress(Math.min(99, done * 100 / Math.max(1, classes)), "SpotBugs 단계 " + pass + " · " + done + "/" + classes + " 클래스");
                    }
                }
            }

            @Override
            public void finishPerClassAnalysis() {
                // 쓰지 않는다
            }
        };
    }

    static int minPriority(Rule.Def d) {
        Object v = d.params().get("minPriority");
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return v == null ? Priorities.NORMAL_PRIORITY : Integer.parseInt(v.toString().trim());
        } catch (NumberFormatException e) {
            return Priorities.NORMAL_PRIORITY;
        }
    }

    static boolean excluded(Rule.Def d, String type) {
        if (!(d.params().get("exclude") instanceof List<?> l)) {
            return false;
        }
        for (Object g : l) {
            // * 만 글롭, 나머지 글자는 그대로 — 사용자 값에 ( [ + 가 들어도 정규식 예외가 안 난다(마무리 리뷰)
            StringBuilder re = new StringBuilder();
            for (String part : String.valueOf(g).trim().split("\\*", -1)) {
                re.append(re.length() == 0 ? "" : ".*").append(Pattern.quote(part));
            }
            if (Pattern.compile(re.toString()).matcher(type).matches()) {
                return true;
            }
        }
        return false;
    }

    static String severity(int priority) {
        return priority <= Priorities.HIGH_PRIORITY ? "error" : priority == Priorities.NORMAL_PRIORITY ? "warn" : "info";
    }

    /** …/WEB-INF/classes 면 옆 lib */
    static Path webInfLib(Path classesDir) {
        Path abs = classesDir.toAbsolutePath().normalize();
        Path parent = abs.getParent();
        Path name = abs.getFileName();
        Path parentName = parent == null ? null : parent.getFileName();
        return name != null && parentName != null && name.toString().equalsIgnoreCase("classes") && parentName.toString().equalsIgnoreCase("WEB-INF")
                ? parent.resolve("lib") : null;
    }

    private static int countClasses(Path dir) {
        try (Stream<Path> s = Files.walk(dir)) {
            return (int) s.filter(p -> p.getFileName().toString().endsWith(".class")).count();
        } catch (IOException e) {
            return 0;
        }
    }

    /** 소스 루트 아래 .java — 파일 이름 → 상대 경로들 */
    static Map<String, List<String>> sourceIndex(Path root) {
        Map<String, List<String>> m = new HashMap<>();
        if (root == null || !Files.isDirectory(root)) {
            return m;
        }
        try (Stream<Path> s = Files.walk(root)) {
            s.filter(p -> p.getFileName().toString().endsWith(".java")).forEach(p -> m.computeIfAbsent(p.getFileName().toString()
                    .toLowerCase(Locale.ROOT), k -> new ArrayList<>()).add(root.relativize(p).toString().replace('\\', '/')));
        } catch (IOException e) {
            return m;
        }
        return m;
    }

    /** SpotBugs 소스 경로(패키지 경로/파일 이름) → 소스 루트 아래 상대 경로. 끝이 맞는 것 가운데 가장 짧은 것, 없으면 그대로 */
    static String mapFile(Map<String, List<String>> index, String sourcePath) {
        if (sourcePath == null || sourcePath.isEmpty()) {
            return "?";
        }
        String sp = sourcePath.replace('\\', '/');
        String name = sp.substring(sp.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
        String best = null;
        for (String rel : index.getOrDefault(name, List.of())) {
            if ((rel.equals(sp) || rel.endsWith("/" + sp)) && (best == null || rel.length() < best.length())) {
                best = rel;
            }
        }
        return best == null ? sp : best;
    }
}
