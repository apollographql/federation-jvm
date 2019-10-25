package com.apollographql.federation.user;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration
public class TestUserConfiguration {

    @Bean
    public UserService userService() {
        return new UserService();
    }
}
