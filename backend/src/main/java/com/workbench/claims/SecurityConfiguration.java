package com.workbench.claims;

import com.workbench.claims.api.ApiError;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfException;

@Configuration
class SecurityConfiguration {
  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  SecurityFilterChain security(HttpSecurity http) throws Exception {
    return http.csrf(csrf -> csrf.spa())
        .requestCache(cache -> cache.disable())
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/actuator/health", "/api/auth/csrf", "/api/auth/login")
                    .permitAll()
                    .requestMatchers("/api/staff/**")
                    .hasRole("OFFICER")
                    .requestMatchers("/api/claims", "/api/claims/**")
                    .hasRole("CLAIMANT")
                    .requestMatchers("/api/**")
                    .authenticated()
                    .anyRequest()
                    .permitAll())
        .formLogin(
            form ->
                form.loginProcessingUrl("/api/auth/login")
                    .successHandler((request, response, auth) -> response.setStatus(204))
                    .failureHandler(
                        (request, response, ex) ->
                            ApiError.write(response, 401, "BAD_CREDENTIALS")))
        .logout(
            logout ->
                logout
                    .logoutUrl("/api/auth/logout")
                    .logoutSuccessHandler(
                        (request, response, auth) -> {
                          if (auth == null) ApiError.write(response, 401, "UNAUTHENTICATED");
                          else response.setStatus(204);
                        }))
        .exceptionHandling(
            errors ->
                errors
                    .authenticationEntryPoint(
                        (request, response, ex) -> ApiError.write(response, 401, "UNAUTHENTICATED"))
                    .accessDeniedHandler(
                        (request, response, ex) ->
                            ApiError.write(
                                response,
                                403,
                                ex instanceof CsrfException ? "CSRF_INVALID" : "FORBIDDEN")))
        .build();
  }
}
