package org.gexuy.cnc.tunnel;

public class LoginResponse {
    private String apiKey;

    public LoginResponse() {}

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public String toString() {
        return "LoginResponse{" +
                "apiKey='" + apiKey + '\'' +
                '}';
    }
}
