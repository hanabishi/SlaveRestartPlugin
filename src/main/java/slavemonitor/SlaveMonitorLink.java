package slavemonitor;

import hudson.Extension;
import hudson.Util;
import hudson.model.ManagementLink;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.Map.Entry;

import org.kohsuke.stapler.bind.JavaScriptMethod;

@Extension
public class SlaveMonitorLink extends ManagementLink {

    public SlaveMonitor slaveMonitor = null;
    public static SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public SlaveMonitorLink() {
        slaveMonitor = SlaveMonitor.getInstance();
    }

    @Override
    public String getDisplayName() {
        return "Slave Restart Monitor";
    }

    @Override
    public String getIconFileName() {
        return "/plugin/SlaveRestartPlugin/slaverestartmonitor.jpg";
    }

    @Override
    public String getDescription() {
        return "Monitors the slaves to see if they need to restart, if so the slave is allocated to 100% and then restarted";
    }

    @Override
    public String getUrlName() {
        return "slaverestartmonitor";
    }

    @JavaScriptMethod
    public String restartSlave(String id) {
        if (slaveMonitor != null) {
            slaveMonitor.restartSlave(id);
        }
        return getStatus();
    }

    @JavaScriptMethod
    public String kill() {
        if (slaveMonitor != null) {
            slaveMonitor.kill();
        }
        return getStatus();
    }

    @JavaScriptMethod
    public String checkNow() {
        if (slaveMonitor != null) {
            slaveMonitor.checkNow();
        }
        return getStatus();
    }

    public String getStatus() {
        int needsRestart = 0;
        for (Entry<String, SlaveWatcher> config : slaveMonitor.getSlaves().entrySet()) {
            needsRestart += (config.getValue().isRestartSlave() ? 1 : 0);
        }

        return "There are " + slaveMonitor.getSlaves().size() + " slaves and " + needsRestart + " needs to restart";
    }

    @JavaScriptMethod
    public String rebuild() {
        if (slaveMonitor != null) {
            slaveMonitor.reBuildSlaveList();
        }
        return getStatus();
    }

    public LinkedList<SlaveWatcher> getSlaves() {
        LinkedList<SlaveWatcher> slaveList = new LinkedList<SlaveWatcher>();
        if (slaveMonitor == null) {
            return slaveList;
        }

        for (Entry<String, SlaveWatcher> config : slaveMonitor.getSlaves().entrySet()) {
            slaveList.add(config.getValue());
        }
        Collections.sort(slaveList, new Comparator<SlaveWatcher>() {
            @Override
            public int compare(SlaveWatcher o1, SlaveWatcher o2) {
                String path1 = Util.fixNull(o1.getComputer().getDisplayName());
                String path2 = Util.fixNull(o2.getComputer().getDisplayName());
                return path1.compareTo(path2);
            }
        });
        return slaveList;
    }
}
