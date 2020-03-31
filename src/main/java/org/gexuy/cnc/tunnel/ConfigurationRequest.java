package org.gexuy.cnc.tunnel;

public class ConfigurationRequest {
    private String name;
    private String password;
    private int maxClients;
    private int port;
    private boolean tunnelEnabled;
    private String adminUsername;

    public ConfigurationRequest() {}

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
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
        return "Configuration: " +
                "Name = " + this.name + "\n" +
                "Password = " + this.password + "\n" +
                "Max Clients = " + this.maxClients +
                "Port = " + this.port + "\n" +
                "Tunnel Enabled = " + this.tunnelEnabled + "\n" +
                "Admin Username = " + this.adminUsername;
    }
}
