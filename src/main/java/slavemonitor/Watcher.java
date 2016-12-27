package slavemonitor;

import hudson.Launcher;
import hudson.model.Executor;
import hudson.model.TaskListener;
import hudson.model.Computer;
import hudson.model.Node;

import java.io.IOException;
import java.util.Date;
import java.util.LinkedList;

import jenkins.model.Jenkins;
import support.CommandRunner;
import support.SlaveReservationExecutable;
import support.SlaveReservationTask;

public class Watcher {
    public static String SESSION_KEY = "reg query \"hklm\\System\\CurrentControlSet\\Control\\Session Manager\" /v PendingFileRenameOperations";
    public static String REBOOT_KEY = "reg query \"hklm\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\WindowsUpdate\\Auto Update\\RebootRequired\"";

    public static int NO_RESTART_REQUIRED = 0;
    public static int CHECKING_RESTART_STATUS = 1;
    public static int RESTART_NEEDED_PENDING = 2;
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
    private boolean forceCheck = false;

    public Watcher(Computer computer, Node node) throws IOException, InterruptedException {
        this.computer = computer;
        this.node = node;
        doCheckForRestart();
    }

    public String getFormatedDate() {
        return (lastChecked != null) ? SlaveMonitorLink.dateFormat.format(lastChecked) : "Never checked";
    }

    public void setError(String message) {
        this.lastChecked = new Date();
        this.status = Watcher.CHECK_FAILED;
        this.errorMessage = message;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Computer getComputer() {
        return this.computer;
    }

    private String getStatusText() {
        if (status == Watcher.NO_RESTART_REQUIRED) {
            return "<span style='color:greenk'>No restart required</span>";
        } else if (status == Watcher.CHECKING_RESTART_STATUS) {
            return "<span style='color:blue'>Checking if restart is needed</span>";
        } else if (status == Watcher.RESTART_NEEDED_PENDING) {
            return "<span style='color:orange'>Restart needed, waiting for all executors to be free</span>";
        } else if (status == Watcher.RESTART_INITIATED) {
            return "<span style='color:orange'>Restarting</span>";
        } else if (status == Watcher.CHECK_FAILED) {
            return "<span style='color:red'>Failed to check restart status</span>";
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
        status = Watcher.NO_RESTART_REQUIRED;
    }

    public boolean isOnline() {
        return (computer != null) ? computer.isOnline() : false;
    }

    public synchronized void kill() {
        this.setKeepRunning(false);
        this.setRestartSlave(false);
        for (SlaveReservationTask worker : reservationWorkers) {
            worker.killProcess();
        }
        reservationWorkers.clear();
        this.notifyAll();
    }

    private Launcher getLauncher() {
        try {
            return node.createLauncher(TaskListener.NULL);
        } catch (NullPointerException e) {
            status = Watcher.FAILED_TO_CREATE_LAUNCHER;
            return null;
        }
    }

    private synchronized void doCheckForRestart() throws IOException, InterruptedException {
        Launcher launcher = getLauncher();
        if (launcher == null) {
            return;
        }
        status = Watcher.CHECKING_RESTART_STATUS;
        int code = CommandRunner.runCommandCode(Watcher.REBOOT_KEY, launcher)
                + CommandRunner.runCommandCode(Watcher.SESSION_KEY, launcher);
        if (code != 2) {
            status = Watcher.RESTART_NEEDED_PENDING;
            setRestartSlave(true);
        } else {
            status = Watcher.NO_RESTART_REQUIRED;
        }
    }

    public static synchronized void reserveTask(SlaveReservationTask task) {
        Jenkins.getInstance().getQueue().schedule(task, 0);
    }

    @SuppressWarnings("unused")
    private synchronized void doRestartIfPossible() throws IllegalThreadStateException, IOException, Exception {
        boolean noRunningServices = true;
        if (reservationWorkers.isEmpty()) {
            for (Executor exec : computer.getExecutors()) {
                SlaveReservationTask task = new SlaveReservationTask(node, "Slave needs to restart", this);
                reservationWorkers.add(task);
                reserveTask(task);
            }
        } else if (reservationWorkers.size() < computer.getExecutors().size()) {
            for (int i = 0; i < (computer.getExecutors().size() - reservationWorkers.size()); i++) {
                SlaveReservationTask task = new SlaveReservationTask(node, "Slave needs to restart", this);
                reservationWorkers.add(task);
                reserveTask(task);
            }
        }

        for (Executor exec : computer.getExecutors()) {
            noRunningServices &= exec == null || (exec.getCurrentExecutable() instanceof SlaveReservationExecutable);
        }

        if (noRunningServices) {
            status = Watcher.RESTART_INITIATED;
            Launcher launcher = getLauncher();
            CommandRunner.runCommandException("shutdown /r /f /t 5", launcher);
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

    public synchronized void slaveStartedRunning() {
        this.notifyAll();
    }

    public void check() {
        if (isOnline()) {
            try {
                if (!isRestartSlave() || forceCheck) {
                    forceCheck = false;
                    doCheckForRestart();
                }
                if (isRestartSlave()) {
                    doRestartIfPossible();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public LinkedList<String> getRestarts() {
        return restarts;
    }

    public void setRestarts(LinkedList<String> restarts) {
        this.restarts = restarts;
    }

    public boolean isRestartSlave() {
        return restartSlave;
    }

    public synchronized void setRestartSlave(boolean restartSlave) {
        this.restartSlave = restartSlave;
        this.notifyAll();
    }

    public boolean isKeepRunning() {
        return keepRunning;
    }

    public void setKeepRunning(boolean keepRunning) {
        this.keepRunning = keepRunning;
    }

}
