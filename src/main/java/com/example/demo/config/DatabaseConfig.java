package com.example.demo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class DatabaseConfig {

  @Value("${DATABASE_URL}")
  private String databaseUrl;

  @Value("${DATABASE_USERNAME:}")
  private String username;

  @Value("${DATABASE_PASSWORD:}")
  private String password;

  @Bean
  public DataSource dataSource() {
    String url = databaseUrl;

    // Fix: If the URL starts with postgres://, change it to jdbc:postgresql://
    if (url.startsWith("postgres://")) {
      url = url.replace("postgres://", "jdbc:postgresql://");
    }

    // If it doesn't have jdbc: at all, prepend it
    if (!url.startsWith("jdbc:")) {
      url = "jdbc:postgresql://" + url.split("://")[1];
    }

    DataSourceBuilder<?> dataSourceBuilder = DataSourceBuilder.create();
    dataSourceBuilder.url(url);

    // Only set username/password if they are explicitly provided and not empty
    if (username != null && !username.isEmpty()) {
      dataSourceBuilder.username(username);
    }
    if (password != null && !password.isEmpty()) {
      dataSourceBuilder.password(password);
    }

    return dataSourceBuilder.build();
  }
}
