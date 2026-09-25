package com.anvex.tools;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.InetSocketAddress;

public final class SmokeClient {

    private SmokeClient() {}

    public static void main(String[] args) {
        if (args.length < 7) {
            System.out.println("Usage: SmokeClient <host> <port> <sourceId> <count> <intervalMs> <username> <password> [clientType]");
            System.out.println("Example: SmokeClient 127.0.0.1 9090 192.168.1.50 20 100 lab_target wrongpass ATTACKER");
            System.exit(1);
        }

        String host = args[0];
        int port = Integer.parseInt(args[1]);
        String sourceId = args[2];
        int count = Integer.parseInt(args[3]);
        long intervalMs = Long.parseLong(args[4]);
        String username = args[5];
        String password = args[6];
        String clientType = args.length > 7 ? args[7] : "ATTACKER";

        int success = 0;
        int failure = 0;
        int blocked = 0;
        int error = 0;

        for (int i = 1; i <= count; i++) {
            String response = sendLogin(host, port, username, password, clientType, sourceId);
            System.out.printf("[%d] %s%n", i, response);
            switch (response) {
                case "SUCCESS" -> success++;
                case "FAILURE" -> failure++;
                case "BLOCKED" -> blocked++;
                default -> error++;
            }
            if (intervalMs > 0 && i < count) {
                try {
                    Thread.sleep(intervalMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        System.out.printf("SUMMARY: success=%d failure=%d blocked=%d error=%d%n", success, failure, blocked, error);
        if (error > 0) System.exit(1);
    }

    private static String sendLogin(String host, int port, String username, String password,
                                    String clientType, String sourceId) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 3000);
            socket.setSoTimeout(10000);
            try (
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            out.println("LOGIN|" + username + "|" + password + "|" + clientType + "|" + sourceId);
            String response = in.readLine();
            return response != null ? response : "ERROR|No response";
            }
        } catch (Exception e) {
            return "ERROR|" + e.getMessage();
        }
    }
}
