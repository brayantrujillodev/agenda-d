package co.edu.fet.agendad.gateway;
import org.springframework.boot.SpringApplication;import org.springframework.boot.autoconfigure.SpringBootApplication;import org.springframework.context.annotation.Bean;import org.springframework.web.client.RestClient;
@SpringBootApplication public class GatewayApplication {public static void main(String[] a){SpringApplication.run(GatewayApplication.class,a);}@Bean RestClient restClient(){return RestClient.create();}}
