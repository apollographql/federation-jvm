package com.apollographql.federation.springexample;

import com.apollographql.federation.graphqljava.tracing.FederatedTracingInstrumentation;
import graphql.execution.instrumentation.Instrumentation;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class App {
    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }

    @Bean
    public Instrumentation addFederatedTracing() {
        return new FederatedTracingInstrumentation(new FederatedTracingInstrumentation.Options(true));
    }
}
