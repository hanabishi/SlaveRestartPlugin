package slavemonitor;

import hudson.model.Computer;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map.Entry;

import jenkins.model.Jenkins;

public class SlaveMonitor extends Thread {

    private boolean isAlive = true;
    private static SlaveMonitor instance = null;
    private HashMap<String, Watcher> slaves = new HashMap<String, Watcher>();

    public synchronized static SlaveMonitor getInstance() {
        if (instance == null) {
            instance = new SlaveMonitor();
            instance.start();
        }
        return instance;
    }

    @Override
    public void run() {
        while (isAlive) {
            if (isAlive) {
                reBuildSlaveList();
                for (Watcher watcher : getSlaves().values()) {
                    watcher.check();
                }
            }

            try {
                this.wait(60000);
            } catch (Throwable t) {
            }
        }
    }

    public synchronized void reBuildSlaveList() {
        LinkedList<String> computers = new LinkedList<String>();

        for (Computer computer : Jenkins.getInstance().getComputers()) {
            if (!computer.isOnline()) {
                continue;
            }
            computers.add(computer.getDisplayName());
            if (!getSlaves().containsKey(computer.getDisplayName()) && !computer.isUnix()
                    && !computer.getDisplayName().equalsIgnoreCase("master")) {
                try {
                    if (!computer.isUnix()) {
                        getSlaves().put(computer.getDisplayName(), new Watcher(computer, computer.getNode()));
                    }
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        for (Entry<String, Watcher> slaveEntry : slaves.entrySet()) {
            if (!computers.contains(slaveEntry.getKey())) {
                slaveEntry.getValue().kill();
                slaves.remove(slaveEntry.getKey());
            }
        }
    }

    public synchronized void restartSlave(String id) {
        for (Entry<String, Watcher> slaveEntry : slaves.entrySet()) {
            if (slaveEntry.getValue().getID() == Integer.parseInt(id)) {
                slaveEntry.getValue().setRestartSlave(true);
                return;
            }
        }
    }

    public synchronized void kill() {
        isAlive = false;
        this.notifyAll();

        for (Entry<String, Watcher> slaveEntry : slaves.entrySet()) {
            slaveEntry.getValue().kill();
        }
        slaves.clear();
    }

    public synchronized void checkNow() {
        for (Entry<String, Watcher> slaveEntry : slaves.entrySet()) {
            slaveEntry.getValue().checkNow();
        }
    }

    public HashMap<String, Watcher> getSlaves() {
        return slaves;
    }

    public void setSlaves(HashMap<String, Watcher> slaves) {
        this.slaves = slaves;
    }

}
