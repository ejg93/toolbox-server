package kr.ejg.toolbox.cli;

import kr.ejg.toolbox.core.Version;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
        name = "toolbox-server",
        mixinStandardHelpOptions = true,
        versionProvider = Main.VersionProvider.class,
        description = "폐쇄망용 로컬 도구 서버. 127.0.0.1 에만 뜬다.",
        subcommands = {Serve.class, VersionCommand.class, MakeTemplates.class,
                MetaCommands.Api.class, MetaCommands.Snapshot.class, MetaCommands.Snapshots.class, MetaCommands.Diff.class,
                DocCommands.Deliverable.class, DocCommands.Ddl.class, DocCommands.Dto.class, DocCommands.Generate.class})
public final class Main implements Runnable {

    public static void main(String[] args) {
        int code = commandLine().execute(args);
        System.exit(code);
    }

    public static CommandLine commandLine() {
        return new CommandLine(new Main());
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
