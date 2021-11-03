package ru.geekbrains.client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Main extends Application {
    public static final String WNDTITLE_APPNAME = "March Chat";
    private static final Logger LOGGER = LogManager.getLogger(Main.class);

    public static void main (String[] args) {
        LOGGER.fatal("---------------------------------------------------------------------------------");
        LOGGER.info("main() : начало");
        launch(args);
        LOGGER.info("main() : конец");
    }

    @Override public void start (Stage primaryStage) throws Exception {
        LOGGER.info("start() : начало");
        Parent root = FXMLLoader.load (getClass().getResource("/window.fxml"));
        primaryStage.setTitle (WNDTITLE_APPNAME);
        Scene scene = new Scene (root, 350, 250);
        primaryStage.setScene (scene);
        primaryStage.show();
        primaryStage.setMinWidth(350);
        primaryStage.setMinHeight(250);
        LOGGER.info("start() : конец");
    }
}

