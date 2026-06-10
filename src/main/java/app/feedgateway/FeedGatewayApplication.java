package app.feedgateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Map;

@SpringBootApplication
public class FeedGatewayApplication {
    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(FeedGatewayApplication.class);
        application.setDefaultProperties(Map.of("server.port", GatewaySettings.intValue("APP_WEB_PORT", 8091, 1)));
        application.run(args);
    }
}
