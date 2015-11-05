package support;

import hudson.Launcher;
import hudson.Launcher.ProcStarter;

import java.io.IOException;
import java.io.PipedOutputStream;
import java.util.LinkedList;

public class CommandRunner {

    public static int runCommandWithCode(String commandLine, Launcher launcher) throws IOException,
            InterruptedException {
        ProcStarter proc = launcher.launch();
        String command = "cmd /c " + commandLine;
        proc.cmdAsSingleString(command);

        return proc.join();
    }

    public static LinkedList<String> runCommand(String commandLine, Launcher launcher) throws Exception, IOException,
            InterruptedException, IllegalThreadStateException {
        LinkedList<String> output = new LinkedList<String>();
        PipedOutputStream pos = null;
        StreamReaderThread srt = null;
        ProcStarter proc = null;

        try {
            proc = launcher.launch();
            pos = new PipedOutputStream();
            srt = new StreamReaderThread(pos);
            proc.stdout(pos);
            proc.stderr(pos);
            String command = "cmd /c \"" + commandLine + "\"";
            proc.cmdAsSingleString(command);
            srt.start();

            int code = proc.join();
            try {
                pos.close();
                srt.join();
            } catch (Exception e) {}

            output = srt.getOutput();

            if (code != 0) {
                throw new Exception("Failed to run command");
            }
        } finally {
            try {
                if (pos != null) {
                    pos.close();
                }
            } catch (IOException e) {}

            if (srt != null) {
                srt.kill();
            }
        }
        return output;
    }
}
