package com.anvex.app;

import com.anvex.util.AppConfig;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class AnvexApplication extends Application {

    private static final Logger logger = LoggerFactory.getLogger(AnvexApplication.class);

    @Override
    public void start(Stage primaryStage) throws IOException {
        logger.info("Starting ANVEX - Local Cybersecurity Laboratory");
        
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

    public static void main(String[] args) {
        AppConfig.initialize();
        launch(args);
    }
}