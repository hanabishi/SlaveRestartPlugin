package slavemonitor;

import hudson.model.ResourceList;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.Queue.Executable;
import hudson.model.Queue.TransientTask;
import hudson.model.queue.AbstractQueueTask;
import hudson.model.queue.CauseOfBlockage;

import java.io.IOException;

public class SlaveReservationTask extends AbstractQueueTask implements TransientTask {
    private final Node node;
    private final String projectName;
    private SlaveReservationExecutable exec = null;
    private SlaveWatcher watcher;

    public SlaveReservationTask(Node node, String projectName, SlaveWatcher watcher) {
        this.node = node;
        this.projectName = projectName;
        this.watcher = watcher;
    }

    @Override
    public boolean isBuildBlocked() {
        return false;
    }

    @Override
    public String getWhyBlocked() {
        return null;
    }

    @Override
    public CauseOfBlockage getCauseOfBlockage() {
        return null;
    }

    @Override
    public String getName() {
        return projectName + " (" + node.getDisplayName() + ")";
    }

    @Override
    public String getFullDisplayName() {
        return projectName + " (" + node.getDisplayName() + ")";
    }

    @Override
    public void checkAbortPermission() {
    }

    @Override
    public boolean hasAbortPermission() {
        return true;
    }

    @Override
    public String getUrl() {
        return null;
    }

    @Override
    public boolean isConcurrentBuild() {
        return false;
    }

    @Override
    public String getDisplayName() {
        return getFullDisplayName();
    }

    @Override
    public Label getAssignedLabel() {
        return node.getSelfLabel();
    }

    @Override
    public Node getLastBuiltOn() {
        return node;
    }

    @Override
    public long getEstimatedDuration() {
        return -1;
    }

    public void killProcess() {
        if (getExec() != null) {
            getExec().kill();
        }
    }

    @Override
    public Executable createExecutable() throws IOException {
        setExec(new SlaveReservationExecutable(this));
        watcher.slaveStartedRunning();
        return getExec();
    }

    @Override
    public Object getSameNodeConstraint() {
        return null;
    }

    @Override
    public ResourceList getResourceList() {
        return new ResourceList();
    }

    public SlaveReservationExecutable getExec() {
        return exec;
    }

    public void setExec(SlaveReservationExecutable exec) {
        this.exec = exec;
    }
}
