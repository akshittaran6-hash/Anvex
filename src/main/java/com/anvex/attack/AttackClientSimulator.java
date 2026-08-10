package com.anvex.attack;

import com.anvex.util.AppConfig;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public final class AttackClientSimulator {

    public String attempt(String username, String password) throws IOException {
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
            out.println("LOGIN|" + username + "|" + password + "|ATTACKER");

            String response = in.readLine();

            if (response == null) {
                throw new IOException("Server closed connection without response");
            }

            return response;
        }
    }
}
