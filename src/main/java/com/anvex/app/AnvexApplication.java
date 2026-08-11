package com.anvex.app;

import com.anvex.event.EventBus;
import com.anvex.persistence.DatabaseManager;
import com.anvex.persistence.UserRepository;
import com.anvex.server.AuthenticationServer;
import com.anvex.util.AppConfig;
import com.anvex.util.PasswordUtil;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class AnvexApplication extends Application {
    private static final Logger logger = LoggerFactory.getLogger(AnvexApplication.class);
    private AuthenticationServer networkServer;

    @Override
    public void start(Stage primaryStage) throws IOException {
        logger.info("Starting ANVEX - Local Cybersecurity Laboratory");

        if (AppConfig.NETWORK_LAB_ENABLED) {
            startNetworkLab();
        }

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/dashboard.fxml"));
        Scene scene = new Scene(loader.load());
        scene.getStylesheets().add(getClass().getResource("/css/dashboard.css").toExternalForm());

        primaryStage.setTitle("ANVEX - Local Cybersecurity Laboratory");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(1200);
        primaryStage.setMinHeight(800);
        primaryStage.show();
        logger.info("ANVEX dashboard launched");
    }

    private void startNetworkLab() throws IOException {
        DatabaseManager db = DatabaseManager.getInstance();
        db.initialize();

        UserRepository users = new UserRepository(db);
        if (users.findByUsername(AppConfig.NETWORK_LAB_TARGET_USERNAME).isEmpty()) {
            users.createUser(
                    AppConfig.NETWORK_LAB_TARGET_USERNAME,
                    PasswordUtil.hashPassword(AppConfig.NETWORK_LAB_TARGET_PASSWORD),
                    "LAB_TARGET");
            logger.info("Created controlled lab target account: {}", AppConfig.NETWORK_LAB_TARGET_USERNAME);
        }

        networkServer = new AuthenticationServer(db, new EventBus());
        networkServer.start(0);
        logger.info("Private-LAN Network Lab server started on {}:{}",
                AppConfig.SERVER_HOST, AppConfig.SERVER_PORT);
    }

    @Override
    public void stop() {
        if (networkServer != null) {
            networkServer.stop();
            networkServer = null;
        }
    }

    public static void main(String[] args) {
        AppConfig.initialize();
        launch(args);
    }
}
