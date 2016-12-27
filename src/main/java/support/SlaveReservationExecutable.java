package support;

import hudson.model.Queue.Executable;

public class SlaveReservationExecutable implements Executable {
    private final SlaveReservationTask parent;
    private boolean isAlive = true;

    public SlaveReservationExecutable(SlaveReservationTask parent) {
        this.parent = parent;
    }

    @Override
    public SlaveReservationTask getParent() {
        return parent;
    }

    public synchronized void kill() {
        isAlive = false;
        this.notifyAll();
    }

    @Override
    public synchronized void run() {
        try {
            while (isAlive) {
                this.wait(60000);
            }
        } catch (Throwable e) {
        }
        isAlive = false;
    }

    @Override
    public long getEstimatedDuration() {
        return 60;
    }
}
