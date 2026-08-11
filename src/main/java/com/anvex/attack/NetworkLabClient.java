package com.anvex.attack;

import com.anvex.util.AppConfig;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * Small, bounded client for the ANVEX private-LAN demonstration.
 * It never sends more than NETWORK_LAB_MAX_ATTEMPTS requests in one run.
 */
public final class NetworkLabClient {
    private NetworkLabClient() {}

    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : AppConfig.SERVER_HOST;
        String username = args.length > 1 ? args[1] : AppConfig.NETWORK_LAB_TARGET_USERNAME;
        String mode = args.length > 2 ? args[2].toUpperCase() : "BASELINE";

        System.out.println("ANVEX Network Lab client");
        System.out.println("Target: " + host + ":" + AppConfig.SERVER_PORT);
        System.out.println("Mode: " + mode + " (bounded to " + AppConfig.NETWORK_LAB_MAX_ATTEMPTS + " requests)");

        for (int i = 0; i < AppConfig.NETWORK_LAB_MAX_ATTEMPTS; i++) {
            String password = "wrong_lab_password_" + i;
            String response = attempt(host, username, password);
            System.out.printf("%02d -> %s%n", i + 1, response);
            if ("BLOCKED".equals(response)) {
                break;
            }
        }
    }

    private static String attempt(String host, String username, String password) throws IOException {
        try (Socket socket = new Socket(host, AppConfig.SERVER_PORT);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            out.println("LOGIN|" + username + "|" + password + "|" + AppConfig.NETWORK_LAB_CLIENT_TYPE);
            String response = in.readLine();
            return response == null ? "NO_RESPONSE" : response;
        }
    }
}
