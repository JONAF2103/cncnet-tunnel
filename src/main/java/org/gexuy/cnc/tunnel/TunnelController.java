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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
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

    private String serverName;
    private String serverPassword;
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
    private String adminPassword = "admin";
    private Gson gson = new Gson();
    private long heartBeatTimeoutSeconds = 5;
    private String apiKey;

    public TunnelController(String serverName, String serverPassword, int port, int maxClients, String master, String masterPW, int ipLimit) {
        clients = new ConcurrentHashMap<>();

        this.serverName = serverName;
        this.serverPassword = serverPassword;
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
        boolean pwOk = (serverPassword == null);

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

            if (kv[0].equals("password") && !pwOk && kv[1].equals(serverPassword)) {
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

    private void handleStatus(HttpExchange t) throws IOException {
        String response = (maxClients - clients.size()) + " slots free.\n" + clients.size() + " slots in use.\n";
        t.sendResponseHeaders(200, response.length());
        OutputStream os = t.getResponseBody();
        os.write(response.getBytes());
        os.close();
    }

    private void respondJson(HttpExchange t, String jsonString) throws IOException {
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

    private void handleStatusForUI(HttpExchange t) throws IOException {
        Status status = new Status();
        status.setSlotsFree((maxClients - clients.size()));
        status.setSlotsInUse(clients.size());
        status.setServerLog(Main.getLastLogLines());
        respondJson(t, gson.toJson(status));
    }

    private void handleConfigurationForUI(HttpExchange t) throws IOException {
        switch (t.getRequestMethod()) {
            case "GET":
                Configuration configuration = new Configuration();
                configuration.setServerName(serverName);
                configuration.setAdminUsername(adminUsername);
                configuration.setAdminPassword(adminPassword);
                configuration.setMaxClients(maxClients);
                configuration.setServerPassword(serverPassword);
                configuration.setPort(port);
                configuration.setTunnelEnabled(tunnelEnabled);
                respondJson(t, gson.toJson(configuration));
                break;
            case "POST":
                InputStream is = t.getRequestBody();
                Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8);
                Configuration configurationRequest = gson.fromJson(reader, Configuration.class);
                if (configurationRequest.isTunnelEnabled()) {
                    this.tunnelEnabled = false;
                    Main.log("Updating Config: " + configurationRequest.toString());
                    this.serverName = configurationRequest.getServerName();
                    this.serverPassword = configurationRequest.getServerPassword();
                    this.adminUsername = configurationRequest.getAdminUsername();
                    this.adminPassword = configurationRequest.getAdminPassword();
                    this.maxClients = configurationRequest.getMaxClients();
                    this.port = configurationRequest.getPort();
                    Main.log("Config Updated: need to enable tunnel again to get the new config working");
                } else {
                    Main.log("Tunnel Disabled, waiting for new configuration");
                    this.tunnelEnabled = false;
                }
                t.sendResponseHeaders(200, 0);
                t.getResponseBody().close();
                break;
            case "PUT":
                this.tunnelEnabled = true;
                t.sendResponseHeaders(200, 0);
                t.getResponseBody().close();
                break;
            default:
                t.sendResponseHeaders(404, 0);
                t.getResponseBody().close();
                break;
        }
    }

    private void respondWithResourceFile(String fileName, HttpExchange t) throws IOException {
        String response = IOUtils.resourceToString(fileName, StandardCharsets.UTF_8, TunnelController.class.getClassLoader());
        t.sendResponseHeaders(200, response.length());
        OutputStream os = t.getResponseBody();
        os.write(response.getBytes());
        os.close();
    }

    private boolean checkLoginCredentials(HttpExchange t) {
        if (t.getRequestHeaders().containsKey("api_key")) {
            String apiKey = t.getRequestHeaders().getFirst("api_key");
            return this.apiKey.equals(apiKey);
        }
        return false;
    }

    private void handleLoginForUI(HttpExchange t) throws IOException, NoSuchAlgorithmException {
        InputStream is = t.getRequestBody();
        Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8);
        LoginRequest loginRequest = gson.fromJson(reader, LoginRequest.class);
        if (adminUsername.equals(loginRequest.getUsername()) &&
                adminPassword.equals(new String(Base64.getDecoder().decode(loginRequest.getPassword()), StandardCharsets.UTF_8))) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(
                    adminPassword.getBytes(StandardCharsets.UTF_8));
            String apiKey = Base64.getEncoder().encodeToString(hash);
            this.apiKey = apiKey;
            LoginResponse loginResponse = new LoginResponse();
            loginResponse.setApiKey(apiKey);
            respondJson(t, gson.toJson(loginResponse));
        } else {
            t.sendResponseHeaders(401, 0);
        }
    }

    public void setMaintenance() {
        maintenance = true;
        Main.log("Maintenance mode enabled, no new games can be started.\n");

        if (master != null) {
            try {
                URL url = new URL(
                        master + "?version=2"
                                + "&name=" + URLEncoder.encode(serverName, "US-ASCII")
                                + "&password=" + (serverPassword == null ? "0" : "1")
                                + "&port=" + port
                                + "&clients=" + clients.size()
                                + "&maxclients=" + maxClients
                                + (masterPW != null ? "&masterpw=" + URLEncoder.encode(masterPW, "US-ASCII") : "")
                                + "&maintenance=1"
                );
                HttpURLConnection con = (HttpURLConnection) url.openConnection();
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
        t.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        t.getResponseHeaders().add("Access-Control-Allow-Methods", "*");
        t.getResponseHeaders().add("Access-Control-Allow-Headers", "*");
        if (t.getRequestMethod().equals("OPTIONS")) {
            t.sendResponseHeaders(204, 0);
        } else {
            try {
                if (uri.startsWith("/request")) {
                    Main.log("HTTPRequest: " + uri);
                    handleRequest(t);
                } else if (uri.startsWith("/status")) {
                    Main.log("HTTPRequest: " + uri);
                    handleStatus(t);
                } else if (uri.startsWith("/maintenance/")) {
                    Main.log("HTTPRequest: " + uri);
                    handleMaintenance(t);
                } else if (uri.startsWith("/ui")) {
                    handleRequestForUI(t);
                } else {
                    t.sendResponseHeaders(404, 0);
                }
            } catch (IOException e) {
                manageRequestError(t, e);
            }
        }
    }

    private void handleRequestForUI(HttpExchange t) throws IOException {
        String uri = t.getRequestURI().toString();
        try {
            if (uri.endsWith("/ui")) {
                respondWithResourceFile("ui.html", t);
            } else if (uri.endsWith("/ui/login")) {
                handleLoginForUI(t);
            } else if (this.checkLoginCredentials(t)) {
                if (uri.endsWith("/ui/status")) {
                    handleStatusForUI(t);
                } else if (uri.endsWith("/ui/configuration")) {
                    handleConfigurationForUI(t);
                } else {
                    t.sendResponseHeaders(404, 0);
                    t.getResponseBody().close();
                }
            } else {
                t.sendResponseHeaders(401, 0);
                t.getResponseBody().close();
            }
        } catch(IOException | NoSuchAlgorithmException e){
            manageRequestError(t, e);
        }
    }

    private void manageRequestError(HttpExchange t, Exception e) throws IOException {
        Main.log("Error: " + e.getMessage());
        String error = e.getMessage();
        t.sendResponseHeaders(500, error.length());
        OutputStream os = t.getResponseBody();
        os.write(error.getBytes());
        os.close();
    }

    @Override
    public void run() {
        while (rebootTunnelEnabled) {
            if (tunnelEnabled) {
                this.sendHearthBeat();
            } else {
                try {
                    Thread.sleep(heartBeatTimeoutSeconds * 1000);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }
    }

    private void sendHearthBeat() {
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

            for (Iterator<Map.Entry<Short, Client>> i = set.iterator(); i.hasNext(); ) {
                Map.Entry<Short, Client> e = i.next();
                Short id = e.getKey();
                Client client = e.getValue();

                if (client.getLastPacket() + 60000 < now) {
                    Main.log("Client " + e.getKey() + " timed out.");
                    i.remove();
                    pool.add(id);
                }
            }

            Set<Map.Entry<String, Lock>> lset = locks.entrySet();

            for (Iterator<Map.Entry<String, Lock>> i = lset.iterator(); i.hasNext(); ) {
                Map.Entry<String, Lock> e = i.next();
                Lock l = e.getValue();

                if (l.firstRequest + 60000 < now) {
                    Main.log("Lock " + e.getKey() + " released.");
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
