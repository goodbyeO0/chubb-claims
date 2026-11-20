package com.chubb.claims;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
class SecurityConfiguration {
    @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll().anyRequest().authenticated())
            .exceptionHandling(errors -> errors.authenticationEntryPoint((request, response, ex) -> {
                response.setStatus(401);
                response.setContentType("application/json");
                response.getWriter().write("{\"code\":\"UNAUTHENTICATED\",\"message\":\"Sign in to continue.\"}");
            })).build();
    }
}
