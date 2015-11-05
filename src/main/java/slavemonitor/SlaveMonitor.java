package slavemonitor;

import hudson.model.Computer;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map.Entry;

import jenkins.model.Jenkins;

public class SlaveMonitor {

    private static SlaveMonitor instance = null;
    private HashMap<String, SlaveWatcher> slaves = new HashMap<String, SlaveWatcher>();

    public synchronized static SlaveMonitor getInstance() {
        if (instance == null) {
            instance = new SlaveMonitor();
            instance.reBuildSlaveList();
            instance.checkNow();
        }
        return instance;
    }

    public void reBuildSlaveList() {
        LinkedList<String> computers = new LinkedList<String>();

        for (Computer computer : Jenkins.getInstance().getComputers()) {
            SlaveWatcher slave = null;
            computers.add(computer.getDisplayName());

            if (!getSlaves().containsKey(computer.getDisplayName())) {
                slave = new SlaveWatcher(computer, computer.getNode());
                if (!slave.isUnix()) {
                    slave.start();
                    getSlaves().put(computer.getDisplayName(), slave);
                }
            }
        }
        for (Entry<String, SlaveWatcher> slaveEntry : slaves.entrySet()) {
            if (!computers.contains(slaveEntry.getKey()) || slaveEntry.getValue().isUnix()) {
                slaveEntry.getValue().kill();
                slaves.remove(slaveEntry.getKey());
            }
        }
    }

    public void restartSlave(String id) {
        for (Entry<String, SlaveWatcher> slaveEntry : slaves.entrySet()) {
            if (slaveEntry.getValue().getID() == Integer.parseInt(id)) {
                slaveEntry.getValue().setRestartSlave(true);
                return;
            }
        }
    }

    public void kill() {
        for (Entry<String, SlaveWatcher> slaveEntry : slaves.entrySet()) {
            slaveEntry.getValue().kill();
        }
        slaves.clear();
    }

    public void putOnHold() {
        for (Entry<String, SlaveWatcher> slaveEntry : slaves.entrySet()) {
            slaveEntry.getValue().setOnHold(true);
        }
    }

    public void resume() {
        for (Entry<String, SlaveWatcher> slaveEntry : slaves.entrySet()) {
            slaveEntry.getValue().setOnHold(false);
        }
    }

    public void checkNow() {
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
