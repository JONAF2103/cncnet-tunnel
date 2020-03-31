package org.gexuy.cnc.tunnel;

import java.util.List;

public class StatusResponse {
    private int slotsFree;
    private int slotsInUse;
    private List<String> serverLog;

    public StatusResponse() {}

    public int getSlotsFree() {
        return slotsFree;
    }

    public void setSlotsFree(int slotsFree) {
        this.slotsFree = slotsFree;
    }

    public int getSlotsInUse() {
        return slotsInUse;
    }

    public void setSlotsInUse(int slotsInUse) {
        this.slotsInUse = slotsInUse;
    }

    public List<String> getServerLog() {
        return serverLog;
    }

    public void setServerLog(List<String> serverLog) {
        this.serverLog = serverLog;
    }

    @Override
    public String toString() {
        return "Status: " +
                "Slots Free = " + this.slotsFree + "\n" +
                "Slots In Use = " + this.slotsInUse + "\n" +
                "Server Log = " + this.serverLog;
    }
}
