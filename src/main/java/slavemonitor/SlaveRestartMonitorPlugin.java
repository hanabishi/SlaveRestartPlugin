package slavemonitor;

import hudson.Plugin;
import hudson.model.Api;
import jenkins.model.Jenkins;

import org.kohsuke.stapler.export.Exported;

public class SlaveRestartMonitorPlugin extends Plugin {

    public Api getApi() {
        return new Api(this);
    }

    @Exported
    public String getVersion() {
        return (Jenkins.getInstance().getPluginManager().getPlugin(SlaveRestartMonitorPlugin.class) != null) ? Jenkins
                .getInstance().getPluginManager().getPlugin(SlaveRestartMonitorPlugin.class).getVersion() : "";
    }

    @Override
    public void start() throws Exception {
        super.start();
        load();
        SlaveMonitor.getInstance();
    }

    public void kill() {
        SlaveMonitor.getInstance().kill();
    }
}
