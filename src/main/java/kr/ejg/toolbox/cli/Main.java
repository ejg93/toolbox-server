package kr.ejg.toolbox.cli;

import kr.ejg.toolbox.core.Version;

public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        System.out.println("toolbox-server " + Version.get());
    }
}
