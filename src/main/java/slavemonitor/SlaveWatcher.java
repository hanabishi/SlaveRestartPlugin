package slavemonitor;

import hudson.Launcher;
import hudson.model.TaskListener;
import hudson.model.Computer;
import hudson.model.Executor;
import hudson.model.Node;

import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedList;

import jenkins.model.Jenkins;
import support.CommandRunner;
import support.LogHandler;

public class SlaveWatcher extends Thread {
    public static String SESSION_KEY = "reg query \"hklm\\System\\CurrentControlSet\\Control\\Session Manager\" /v PendingFileRenameOperations";
    public static String REBOOT_KEY = "reg query \"hklm\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\WindowsUpdate\\Auto Update\\RebootRequired\"";

    public static int NO_RESTART_REQUIRED = 0;
    public static int CHECKING_RESTART_STATUS = 1;
    public static int RESTART_NEEDED_PENDING = 2;
    public static int RESTART_NEEDED_IGNORED = 3;
    public static int RESTART_INITIATED = 5;
    public static int CHECK_FAILED = 6;
    public static int FAILED_TO_CREATE_LAUNCHER = 7;

    // Configuration
    private Computer computer;
    public Node node;

    public int getID() {
        return computer.getName().hashCode();
    }

    private LinkedList<SlaveReservationTask> reservationWorkers = new LinkedList<SlaveReservationTask>();

    // Verfication
    private Date lastChecked = null;
    private int status = 0;
    private String errorMessage = "";
    private LinkedList<String> restarts = new LinkedList<String>();

    private boolean keepRunning = true;
    private boolean restartSlave = false;
    private boolean onHold = false;
    private boolean forceCheck = false;

    public SlaveWatcher(Computer computer, Node node) {
        this.computer = computer;
        this.node = node;
    }

    public String getFormatedDate() {
        return (lastChecked != null) ? SlaveMonitorLink.dateFormat.format(lastChecked) : "Never checked";
    }

    public void setError(String message) {
        this.lastChecked = new Date();
        this.status = SlaveWatcher.CHECK_FAILED;
        this.errorMessage = message;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Computer getComputer() {
        return this.computer;
    }

    private String getStatusText() {
        if (status == SlaveWatcher.NO_RESTART_REQUIRED) {
            return "<span style='color:greenk'>No restart required</span>";
        } else if (status == SlaveWatcher.CHECKING_RESTART_STATUS) {
            return "<span style='color:blue'>Checking if restart is needed</span>";
        } else if (status == SlaveWatcher.RESTART_NEEDED_PENDING) {
            return "<span style='color:orange'>Restart needed, waiting for all executors to be free</span>";
        } else if (status == SlaveWatcher.RESTART_INITIATED) {
            return "<span style='color:orange'>Restarting</span>";
        } else if (status == SlaveWatcher.CHECK_FAILED) {
            return "<span style='color:red'>Failed to check restart status</span>";
        } else if (status == SlaveWatcher.RESTART_NEEDED_IGNORED) {
            return "<span style='color:purple'>Restart needed, but ignored</span>";
        }
        return "<span style='color:firebrick'>Unknown status</span>";
    }

    public String formatedHeader() {
        return computer.getDisplayName() + ((isOnline()) ? "" : " <span style='color:red'>[OFFLINE]</span> ")
                + " - <i>" + getStatusText() + "</i>";
    }

    public void notifyRestarted() {
        for (SlaveReservationTask worker : reservationWorkers) {
            worker.killProcess();
        }
        reservationWorkers.clear();
        setRestartSlave(false);
        getRestarts().add(SlaveMonitorLink.dateFormat.format(new Date()));
        status = SlaveWatcher.NO_RESTART_REQUIRED;
    }

    public boolean isOnline() {
        return (computer != null) ? computer.isOnline() : false;
    }

    public synchronized void kill() {
        this.setKeepRunning(false);
        this.setRestartSlave(false);
        this.notifyAll();
    }

    private Launcher getLauncher() {
        try {
            return node.createLauncher(TaskListener.NULL);
        } catch (NullPointerException e) {
            status = SlaveWatcher.FAILED_TO_CREATE_LAUNCHER;
            return null;
        }
    }

    private synchronized void doCheckForRestart() throws IOException, InterruptedException {
        Launcher launcher = getLauncher();
        if (launcher == null) {
            return;
        }
        status = SlaveWatcher.CHECKING_RESTART_STATUS;
        int code = CommandRunner.runCommandWithCode(SlaveWatcher.REBOOT_KEY, launcher);
        code += CommandRunner.runCommandWithCode(SlaveWatcher.SESSION_KEY, launcher);
        if (code != 2) {
            if (!computer.getDisplayName().equalsIgnoreCase("master")) {
                status = SlaveWatcher.RESTART_NEEDED_PENDING;
                setRestartSlave(true);
            } else {
                status = SlaveWatcher.RESTART_NEEDED_IGNORED;
            }
        } else {
            status = SlaveWatcher.NO_RESTART_REQUIRED;
        }
    }

    private synchronized void doRestartIfPossible() throws IllegalThreadStateException, IOException, Exception {
        boolean noRunningServices = true;
        if (reservationWorkers.isEmpty()) {
            for (@SuppressWarnings("unused")
            Executor exec : computer.getExecutors()) {
                SlaveReservationTask task = new SlaveReservationTask(node, "Slave needs to restart");
                reservationWorkers.add(task);
                Jenkins.getInstance().getQueue().schedule(task, 0);
            }
        }
        for (Executor exec : computer.getExecutors()) {
            noRunningServices &= exec == null || (exec.getCurrentExecutable() instanceof SlaveReservationExecutable);
        }

        if (noRunningServices) {
            status = SlaveWatcher.RESTART_INITIATED;
            Launcher launcher = getLauncher();
            CommandRunner.runCommand("shutdown /r /f /t 5", launcher);
            int timeout = 0;
            while (computer.isOnline()) {
                timeout++;
                this.wait(5000);
                if (timeout > 100) {
                    break;
                }
            }

            notifyRestarted();
        }
    }

    public synchronized void checkNow() {
        forceCheck = true;
        this.notifyAll();
    }

    @Override
    public void run() {
        int counter = 0;
        while (isKeepRunning()) {
            counter++;
            if (isOnline()) {
                try {
                    // check for updates every hour
                    if (!isRestartSlave() && counter > 60 || forceCheck) {
                        forceCheck = false;
                        doCheckForRestart();
                        counter = 0;
                    }
                    if (isRestartSlave()) {
                        doRestartIfPossible();
                    }
                } catch (IOException e) {
                    LogHandler.printStackTrace(this, e);
                } catch (InterruptedException e) {
                    LogHandler.printStackTrace(this, e);
                } catch (IllegalThreadStateException e) {
                    LogHandler.printStackTrace(this, e);
                } catch (Exception e) {
                    LogHandler.printStackTrace(this, e);
                }
            }
            synchronized (this) {
                try {
                    do {
                        this.wait(60000);
                    } while (!isOnline() || this.onHold);
                } catch (InterruptedException e) {
                    LogHandler.printStackTrace(e);
                }
            }
        }
    }

    public LinkedList<String> getRestarts() {
        return restarts;
    }

    public void setRestarts(LinkedList<String> restarts) {
        this.restarts = restarts;
    }

    public boolean isOnHold() {
        return onHold;
    }

    public void setOnHold(boolean onHold) {
        this.onHold = onHold;
    }

    public boolean isRestartSlave() {
        return restartSlave;
    }

    public void setRestartSlave(boolean restartSlave) {
        this.restartSlave = restartSlave;
    }

    public boolean isKeepRunning() {
        return keepRunning;
    }

    public void setKeepRunning(boolean keepRunning) {
        this.keepRunning = keepRunning;
    }

}
