package kr.ejg.toolbox.cli;

import java.util.concurrent.Callable;
import kr.ejg.toolbox.core.Version;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

@Command(name = "version", description = "버전을 출력한다.")
public final class VersionCommand implements Callable<Integer> {

    @Spec
    CommandSpec spec;

    @Override
    public Integer call() {
        spec.commandLine().getOut().println("toolbox-server " + Version.get());
        spec.commandLine().getOut().flush();
        return 0;
    }
}
