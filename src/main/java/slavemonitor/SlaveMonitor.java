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
    private HashMap<String, SlaveWatcher> slaves = new HashMap<String, SlaveWatcher>();

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
            }

            try {
                wait(60000 * 5);
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
            SlaveWatcher slave = null;
            computers.add(computer.getDisplayName());
            if (!getSlaves().containsKey(computer.getDisplayName()) && !computer.isUnix()
                    && !computer.getDisplayName().equalsIgnoreCase("master")) {
                try {
                    slave = new SlaveWatcher(computer, computer.getNode());
                    if (!computer.isUnix()) {
                        slave.start();
                        getSlaves().put(computer.getDisplayName(), slave);
                    }
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        for (Entry<String, SlaveWatcher> slaveEntry : slaves.entrySet()) {
            if (!computers.contains(slaveEntry.getKey())) {
                slaveEntry.getValue().kill();
                slaves.remove(slaveEntry.getKey());
            }
        }
    }

    public synchronized void restartSlave(String id) {
        for (Entry<String, SlaveWatcher> slaveEntry : slaves.entrySet()) {
            if (slaveEntry.getValue().getID() == Integer.parseInt(id)) {
                slaveEntry.getValue().setRestartSlave(true);
                return;
            }
        }
    }

    public synchronized void kill() {
        isAlive = false;
        this.notifyAll();

        for (Entry<String, SlaveWatcher> slaveEntry : slaves.entrySet()) {
            slaveEntry.getValue().kill();
        }
        slaves.clear();
    }

    public synchronized void checkNow() {
        for (Entry<String, SlaveWatcher> slaveEntry : slaves.entrySet()) {
            slaveEntry.getValue().checkNow();
        }
    }

    public HashMap<String, SlaveWatcher> getSlaves() {
        return slaves;
    }

    public void setSlaves(HashMap<String, SlaveWatcher> slaves) {
        this.slaves = slaves;
    }

}
