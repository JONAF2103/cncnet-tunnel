/*
 * Copyright (c) 2013 Toni Spets <toni.spets@iki.fi>
 * 
 * Permission to use, copy, modify, and distribute this software for any
 * purpose with or without fee is hereby granted, provided that the above
 * copyright notice and this permission notice appear in all copies.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
 * WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR
 * ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 * WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
 * ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF
 * OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
 */
package org.gexuy.cnc.tunnel;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 *
 * @author Toni Spets <toni.spets@iki.fi>
 */
public class TunnelController implements HttpHandler, Runnable {

    private static class Lock {
        public long firstRequest;
        public int games;

        public Lock(long firstRequest) {
            this.firstRequest = firstRequest;
        }

        public void poke() {
            this.games++;
        }
    }

    private final Map<Short, Client> clients;

    private String name;
    private String password;
    private int maxClients;
    private int port;
    private String master;
    private String masterPW;
    private int ipLimit;
    private volatile boolean tunnelEnabled = true;
    private volatile boolean rebootTunnelEnabled = true;
    private Queue<Short> pool;
    private volatile boolean maintenance = false;
    final private ConcurrentHashMap<String, Lock> locks;
    private String adminUsername = "admin";
    private Gson gson = new Gson();

    public TunnelController(String name, String password, int port, int maxClients, String master, String masterPW, int ipLimit) {
        clients = new ConcurrentHashMap<>();

        this.name = name;
        this.password = password;
        this.maxClients = maxClients;
        this.port = port;
        this.master = master;
        this.masterPW = masterPW;
        this.ipLimit = ipLimit;
        this.pool = new ConcurrentLinkedQueue<>();
        this.locks = new ConcurrentHashMap<>();

        long start = System.currentTimeMillis();
        ArrayList<Short> allShort = new ArrayList<>();
        for (short i = Short.MIN_VALUE; i < Short.MAX_VALUE; i++) {
            allShort.add(i);
        }
        Collections.shuffle(allShort);
        pool.addAll(allShort);

        Main.log("TunnelController: Took " + (System.currentTimeMillis() - start) + "ms to initialize pool.");
    }

    public Client getClient(Short clientId) {
        return clients.get(clientId);
    }

    private void handleRequest(HttpExchange t) throws IOException {
        String params = t.getRequestURI().getQuery();
        String requestAddress = t.getRemoteAddress().getAddress().getHostAddress();
        int requestedAmount = 0;
        boolean pwOk = (password == null);

        if (params == null)
            params = "";

        String[] pairs = params.split("&");
        for (String pair : pairs) {
            String[] kv = pair.split("=");
            if (kv.length != 2)
                continue;

            kv[0] = URLDecoder.decode(kv[0], "UTF-8");
            kv[1] = URLDecoder.decode(kv[1], "UTF-8");

            if (kv[0].equals("clients")) {
                requestedAmount = Integer.parseInt(kv[1]);
            }

            if (kv[0].equals("password") && !pwOk && kv[1].equals(password)) {
                pwOk = true;
            }
        }

        if (!pwOk) {
            // Unauthorized
            Main.log("Request was unauthorized.");
            t.sendResponseHeaders(401, 0);
            t.getResponseBody().close();
            return;
        }

        if (requestedAmount < 2 || requestedAmount > 8) {
            // Bad Request
            Main.log("Request had invalid requested amount (" + requestedAmount + ").");
            t.sendResponseHeaders(400, 0);
            t.getResponseBody().close();
            return;
        }

        if (maintenance) {
            // Service Unavailable
            Main.log("Request to start a new game was denied because of maintenance.");
            t.sendResponseHeaders(503, 0);
            t.getResponseBody().close();
            return;
        }

        Lock curLock = locks.get(requestAddress);
        // lock the request ip out until this router is collected
        if (ipLimit > 0 && curLock != null && curLock.games >= ipLimit) {
            // Too Many Requests
            Main.log("Same address tried to request more than " + ipLimit + " routers.");
            t.sendResponseHeaders(429, 0);
            t.getResponseBody().close();
            return;
        }

        StringBuilder ret = new StringBuilder();

        synchronized (clients) {
            if (requestedAmount + clients.size() > maxClients) {
                // Service Unavailable
                Main.log("Request wanted more than we could provide.");
                t.sendResponseHeaders(503, 0);
                t.getResponseBody().close();
                return;
            }

            ret.append("[");

            // for thread safety, we just try to reserve slots (actually we are
            // double synchronized right now, makes little sense)
            ArrayList<Short> reserved = new ArrayList<>();
            for (int i = 0; i < requestedAmount; i++) {
                Short clientId = pool.poll();
                if (clientId != null) {
                    reserved.add(clientId);
                }
            }

            if (reserved.size() == requestedAmount) {
                boolean frist = true;
                for (Short clientId : reserved) {
                    clients.put(clientId, new Client(clientId, reserved));
                    Main.log("Client " + clientId + " allocated.");
                    if (frist) {
                        frist = false;
                    } else {
                        ret.append(",");
                    }
                    ret.append(clientId);
                }
            } else {
                // return our reservations if any
                pool.addAll(reserved);
                // Service Unavailable
                Main.log("Request wanted more than we could provide and we also exhausted our queue.");
                t.sendResponseHeaders(503, 0);
                t.getResponseBody().close();
                return;
            }
            ret.append("]");
        }

        if (ipLimit > 0) {
            synchronized (locks) {
                long now = System.currentTimeMillis();
                Lock l = locks.get(requestAddress);
                if (l == null) {
                    l = new Lock(now);
                }

                l.poke();
                locks.put(requestAddress, l);
            }
        }

        t.sendResponseHeaders(200, ret.length());
        OutputStream os = t.getResponseBody();
        os.write(ret.toString().getBytes());
        os.close();
    }

    private void handleStatus(HttpExchange t, boolean json) throws IOException {
        if (json) {
            StatusResponse statusResponse = new StatusResponse();
            statusResponse.setSlotsFree((maxClients - clients.size()));
            statusResponse.setSlotsInUse(clients.size());
            statusResponse.setServerLog(Main.getLastLogLines());
            respondJson(t, gson.toJson(statusResponse));
        } else {
            String response = (maxClients - clients.size()) + " slots free.\n" + clients.size() + " slots in use.\n";
            Main.log("Response: " + response);
            t.sendResponseHeaders(200, response.length());
            OutputStream os = t.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
    }

    private void respondJson(HttpExchange t, String jsonString) throws IOException {
        Main.log("Response: " + jsonString);
        t.sendResponseHeaders(200, jsonString.length());
        t.getResponseHeaders().set("Content-Type", "application/json");
        OutputStream os = t.getResponseBody();
        os.write(jsonString.getBytes());
        os.close();
    }

    private void handleMaintenance(HttpExchange t) throws IOException {
        setMaintenance();
        t.sendResponseHeaders(200, 0);
        t.getResponseBody().close();
    }

    private void handleConfiguration(HttpExchange t) throws IOException {
        if (t.getRequestHeaders().containsKey("Content-Type") &&
                "application/json".equals(t.getRequestHeaders().getFirst("Content-Type"))) {
            ConfigurationResponse configurationResponse = new ConfigurationResponse();
            configurationResponse.setName(name);
            configurationResponse.setAdminUsername(adminUsername);
            configurationResponse.setMaxClients(maxClients);
            configurationResponse.setPassword(password);
            configurationResponse.setPort(port);
            configurationResponse.setTunnelEnabled(tunnelEnabled);
            respondJson(t, gson.toJson(configurationResponse));
        } else if (t.getRequestMethod().equals("GET")) {
            respondWithPage("configuration.html", t);
        } else if (t.getRequestMethod().equals("POST")) {
            InputStream is = t.getRequestBody();
            Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8);
            ConfigurationRequest configurationRequest = gson.fromJson(reader, ConfigurationRequest.class);
            if (configurationRequest.isTunnelEnabled()) {
                this.tunnelEnabled = false;
                Main.log("Updating Config: " + configurationRequest.toString());
                this.name = configurationRequest.getName();
                this.maxClients = configurationRequest.getMaxClients();
                this.port = configurationRequest.getPort();
                this.password = configurationRequest.getPassword();
                this.adminUsername = configurationRequest.getAdminUsername();
                Main.log("Config Updated: need to enable tunnel again to get the new config working");
            } else {
                Main.log("Tunnel Disabled, waiting for new configuration");
                this.tunnelEnabled = false;
            }
        } else {
            t.sendResponseHeaders(400, 0);
        }
    }

    private void respondWithPage(String pageName, HttpExchange t) throws IOException {
        String response = IOUtils.resourceToString("pages/" + pageName, StandardCharsets.UTF_8, TunnelController.class.getClassLoader());
        Main.log("Response: " + response);
        t.sendResponseHeaders(200, response.length());
        OutputStream os = t.getResponseBody();
        os.write(response.getBytes());
        os.close();
    }

    private void handleLogin(HttpExchange t) throws IOException {
        InputStream is = t.getRequestBody();
        Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8);
        LoginRequest loginRequest  = gson.fromJson(reader, LoginRequest.class);
        if (adminUsername.equals(loginRequest.getUsername()) &&
                password.equals(new String(Base64.getDecoder().decode(loginRequest.getPassword()), StandardCharsets.UTF_8))) {
            respondWithPage("configuration-logged.html", t);
        } else {
            respondWithPage("login-error.html", t);
        }
    }

    public void setMaintenance() {
        maintenance = true;
        Main.log("Maintenance mode enabled, no new games can be started.\n");

        if (master != null) {
            try {
                URL url = new URL(
                    master + "?version=2"
                    + "&name=" + URLEncoder.encode(name, "US-ASCII")
                    + "&password=" + (password == null ? "0" : "1")
                    + "&port=" + port
                    + "&clients=" + clients.size()
                    + "&maxclients=" + maxClients
                    + (masterPW != null ? "&masterpw=" + URLEncoder.encode(masterPW, "US-ASCII") : "")
                    + "&maintenance=1"
                );
                HttpURLConnection con = (HttpURLConnection)url.openConnection();
                con.setRequestMethod("GET");
                con.setConnectTimeout(5000);
                con.setReadTimeout(5000);
                con.connect();
                con.getInputStream().close();
                con.disconnect();
                Main.log("Master notified of maintenance.\n");
            } catch (FileNotFoundException e) {
                Main.log("Master server reported error 404.");
            } catch (IOException e) {
                Main.log("Failed to send heartbeat: " + e.toString());
            }
        }
    }

    @Override
    public void handle(HttpExchange t) throws IOException {
        String uri = t.getRequestURI().toString();
        Main.log("HTTPRequest: " + uri);

        try {
            if (uri.startsWith("/request")) {
                handleRequest(t);
            } else if (uri.startsWith("/status")) {
                handleStatus(t, false);
            } else if (uri.startsWith("/server-status")) {
                handleStatus(t, true);
            } else if (uri.startsWith("/maintenance/")) {
                handleMaintenance(t);
            } else if (uri.startsWith("/configuration")){
                handleConfiguration(t);
            } else if (uri.startsWith("/login")){
                handleLogin(t);
            } else {
                t.sendResponseHeaders(400, 0);
            }
        } catch (IOException e) {
            Main.log("Error: " + e.getMessage());
            String error = e.getMessage();
            t.sendResponseHeaders(500, error.length());
            OutputStream os = t.getResponseBody();
            os.write(error.getBytes());
            os.close();
        }
    }

    @Override
    public void run() {
        while (rebootTunnelEnabled) {
            if (tunnelEnabled) {
                this.runTunnel();
            } else {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }
    }

    private void runTunnel() {
        long lastHeartbeat = 0;

        Main.status("Connecting...");

        Main.log("TunnelController started.");

        boolean connected = false;

        while (tunnelEnabled) {

            long now = System.currentTimeMillis();

            if (maintenance && clients.isEmpty()) {
                Main.log("Tunnel empty, doing maintenance quit.");
                System.exit(0);
                return;
            }

//            if (lastHeartbeat + 60000 < now && master != null && !maintenance) {
//                Main.log("Sending a heartbeat to master server.");
//
//                connected = false;
//                try {
//                    URL url = new URL(
//                            master + "?version=2"
//                                    + "&name=" + URLEncoder.encode(name, "US-ASCII")
//                                    + "&password=" + (password == null ? "0" : "1")
//                                    + "&port=" + port
//                                    + "&clients=" + clients.size()
//                                    + "&maxclients=" + maxClients
//                                    + (masterPW != null ? "&masterpw=" + URLEncoder.encode(masterPW, "US-ASCII") : "")
//                    );
//                    HttpURLConnection con = (HttpURLConnection)url.openConnection();
//                    con.setRequestMethod("GET");
//                    con.setConnectTimeout(5000);
//                    con.setReadTimeout(5000);
//                    con.connect();
//                    con.getInputStream().close();
//                    con.disconnect();
//                    connected = true;
//                } catch (FileNotFoundException e) {
//                    Main.log("Master server reported error 404.");
//                } catch (IOException e) {
//                    Main.log("Failed to send heartbeat: " + e.toString());
//                }
//
//                lastHeartbeat = now;
//            }

            Set<Map.Entry<Short, Client>> set = clients.entrySet();

            for (Iterator<Map.Entry<Short, Client>> i = set.iterator(); i.hasNext();) {
                Map.Entry<Short, Client> e = i.next();
                Short id = e.getKey();
                Client client = e.getValue();

                if (client.getLastPacket() + 60000 < now) {
                    Main.log("Client " + e.getKey() +  " timed out.");
                    i.remove();
                    pool.add(id);
                }
            }

            Set<Map.Entry<String, Lock>> lset = locks.entrySet();

            for (Iterator<Map.Entry<String, Lock>> i = lset.iterator(); i.hasNext();) {
                Map.Entry<String, Lock> e = i.next();
                Lock l = e.getValue();

                if (l.firstRequest + 60000 < now) {
                    Main.log("Lock " + e.getKey() +  " released.");
                    i.remove();
                }
            }

            Main.status(
                    (connected ? "Connected. " : "Disconnected from master. ") +
                            clients.size() + " / " + maxClients + " players online."
            );

            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

}
