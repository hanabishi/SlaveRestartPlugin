package support;

import hudson.Launcher;
import hudson.Launcher.ProcStarter;

import java.io.IOException;

public class CommandRunner {

    public static int runCommandWithCode(String commandLine, Launcher launcher) throws IOException,
            InterruptedException {
        ProcStarter proc = launcher.launch();
        String command = "cmd /c " + commandLine;
        proc.cmdAsSingleString(command);

        return proc.join();
    }

    public static void runCommand(String commandLine, Launcher launcher) throws Exception, IOException,
            InterruptedException, IllegalThreadStateException {
        ProcStarter proc = null;

        proc = launcher.launch();
        String command = "cmd /c \"" + commandLine + "\"";
        proc.cmdAsSingleString(command);

        int code = proc.join();

        if (code != 0) {
            throw new Exception("Failed to run command");
        }
    }
}
