module com.anvex {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;
    requires java.logging;
    requires java.sql;
    requires java.base;
    requires org.slf4j;

    opens com.anvex.app to javafx.fxml;
    opens com.anvex.ui to javafx.fxml;
    opens com.anvex.util to javafx.fxml;

    exports com.anvex.app;
    exports com.anvex.ui;
    exports com.anvex.util;
}