package org.gexuy.cnc.tunnel;

public class Configuration {
    private String serverName;
    private String serverPassword;
    private String adminUsername;
    private String adminPassword;
    private int maxClients;
    private int port;
    private boolean tunnelEnabled;

    public Configuration() {}

    public String getServerName() {
        return serverName;
    }

    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    public String getServerPassword() {
        return serverPassword;
    }

    public void setServerPassword(String serverPassword) {
        this.serverPassword = serverPassword;
    }

    public String getAdminPassword() {
        return adminPassword;
    }

    public void setAdminPassword(String adminPassword) {
        this.adminPassword = adminPassword;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public int getMaxClients() {
        return maxClients;
    }

    public void setMaxClients(int maxClients) {
        this.maxClients = maxClients;
    }

    public boolean isTunnelEnabled() {
        return Boolean.TRUE.equals(tunnelEnabled);
    }

    public void setTunnelEnabled(boolean tunnelEnabled) {
        this.tunnelEnabled = tunnelEnabled;
    }

    public String getAdminUsername() {
        return adminUsername;
    }

    public void setAdminUsername(String adminUsername) {
        this.adminUsername = adminUsername;
    }

    @Override
    public String toString() {
        return "ConfigurationResponse{" +
                "serverName='" + serverName + '\'' +
                ", serverPassword='" + serverPassword + '\'' +
                ", adminUsername='" + adminUsername + '\'' +
                ", adminPassword='" + adminPassword + '\'' +
                ", maxClients=" + maxClients +
                ", port=" + port +
                ", tunnelEnabled=" + tunnelEnabled +
                '}';
    }
}
