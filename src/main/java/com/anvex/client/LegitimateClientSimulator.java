package com.anvex.client;

import com.anvex.util.AppConfig;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public final class LegitimateClientSimulator {

    public String authenticate(String username, String password) throws IOException {
        try (
                Socket socket = new Socket(
                        AppConfig.SERVER_HOST,
                        AppConfig.SERVER_PORT
                );
                PrintWriter out = new PrintWriter(
                        socket.getOutputStream(),
                        true
                );
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(socket.getInputStream())
                )
        ) {
            socket.setSoTimeout(AppConfig.LEGITIMATE_CLIENT_TIMEOUT_MS);

            out.println(
                    "LOGIN|" + username + "|" + password + "|LEGITIMATE"
            );

            String response = in.readLine();

            if (response == null) {
                throw new IOException(
                        "Server closed connection without response"
                );
            }

            return response;
        }
    }
}
