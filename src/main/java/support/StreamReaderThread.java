package support;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.LinkedList;

import org.apache.commons.io.IOUtils;

public class StreamReaderThread extends Thread {
    private final PipedInputStream input;
    private final OutputStream out;
    private final LinkedList<String> output = new LinkedList<String>();
    private BufferedReader outputReader;
    private InputStreamReader is = null;

    public LinkedList<String> getOutput() {
        return output;
    }

    public StreamReaderThread(PipedOutputStream out) throws IOException, NullPointerException {
        super("StreamReader");
        input = new PipedInputStream(out);
        this.out = out;
    }

    @Override
    public void run() {
        try {
            is = new InputStreamReader(input);
            outputReader = new BufferedReader(is);
            String line = null;
            while ((line = outputReader.readLine()) != null) {
                output.add(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            kill();
        }
    }

    public void kill() {
        IOUtils.closeQuietly(outputReader);
        IOUtils.closeQuietly(input);
        IOUtils.closeQuietly(out);
        IOUtils.closeQuietly(is);
    }
}