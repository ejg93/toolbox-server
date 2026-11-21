package kr.ejg.toolbox.cli;

import kr.ejg.toolbox.core.Version;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
        name = "toolbox-server",
        mixinStandardHelpOptions = true,
        versionProvider = Main.VersionProvider.class,
        description = "폐쇄망용 로컬 도구 서버. 127.0.0.1 에만 뜬다.", // tb-ignore common.localhost 127.0.0.1 전용 설계(절대 규칙 1)
        subcommands = {Serve.class, VersionCommand.class, MakeTemplates.class,
                MetaCommands.Api.class, MetaCommands.Snapshot.class, MetaCommands.Snapshots.class, MetaCommands.Diff.class, MetaCommands.Alter.class,
                DocCommands.Deliverable.class, DocCommands.Ddl.class, DocCommands.Lint.class, DocCommands.Dto.class, DocCommands.Generate.class,
                LogicalCommands.class, CodeCommands.Check.class, CodeCommands.Cleanup.class, CodeCommands.Analyze.class, CodeCommands.DeployList.class, SelfTest.class})
public final class Main implements Runnable {

    public static void main(String[] args) {
        int code = commandLine().execute(args);
        System.exit(code);
    }

    public static CommandLine commandLine() {
        return new CommandLine(new Main()).setExecutionExceptionHandler(Main::inputError);
    }

    /** 사용자가 준 경로·파일 탓인 예외는 스택 대신 한 줄과 끝 코드 2(G2-K7). 그 밖은 picocli 기본 처리(스택)로 다시 던진다 */
    static int inputError(Exception e, CommandLine cmd, CommandLine.ParseResult parsed) throws Exception {
        String why = e instanceof java.nio.file.NoSuchFileException n ? "파일이 없다: " + n.getFile()
                : e instanceof java.nio.file.AccessDeniedException a ? "파일을 못 연다(권한·잠김): " + a.getFile()
                : e instanceof java.nio.file.InvalidPathException p ? "경로를 못 읽었다: " + p.getInput() : null;
        if (why == null) {
            throw e;
        }
        cmd.getErr().println("[오류] " + why);
        return 2;
    }

    /** 하위 명령 없이 부르면 사용법 */
    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }

    static final class VersionProvider implements CommandLine.IVersionProvider {
        @Override
        public String[] getVersion() {
            return new String[] {"toolbox-server " + Version.get()};
        }
    }
}
