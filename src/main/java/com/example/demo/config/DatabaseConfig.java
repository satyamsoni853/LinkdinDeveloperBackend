package com.example.demo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.net.URI;

@Configuration
public class DatabaseConfig {

  @Value("${DATABASE_URL:}")
  private String databaseUrl;

  @Bean
  public DataSource dataSource() {
    if (databaseUrl == null || databaseUrl.isBlank()) {
      throw new IllegalStateException("DATABASE_URL environment variable is not set!");
    }

    try {
      // Normalize the scheme for URI parsing
      String uriString = databaseUrl;
      if (uriString.startsWith("jdbc:")) {
        uriString = uriString.substring(5); // remove "jdbc:" prefix for parsing
      }
      if (uriString.startsWith("postgresql://")) {
        uriString = "postgres://" + uriString.substring("postgresql://".length());
      }

      URI dbUri = new URI(uriString);

      // Extract user and password from the URI
      String user = null;
      String pass = null;
      if (dbUri.getUserInfo() != null) {
        String[] userInfo = dbUri.getUserInfo().split(":", 2);
        user = userInfo[0];
        if (userInfo.length > 1) {
          pass = userInfo[1];
        }
      }

      // Build clean JDBC URL (without credentials in the URL)
      String query = dbUri.getQuery();
      // Remove channel_binding parameter (not supported by JDBC driver)
      if (query != null) {
        query = query.replaceAll("&?channel_binding=[^&]*", "").replaceAll("^&", "");
        if (query.isEmpty()) {
          query = null;
        }
      }

      String jdbcUrl = "jdbc:postgresql://" + dbUri.getHost()
          + (dbUri.getPort() > 0 ? ":" + dbUri.getPort() : "")
          + dbUri.getPath()
          + (query != null ? "?" + query : "");

      DataSourceBuilder<?> dataSourceBuilder = DataSourceBuilder.create();
      dataSourceBuilder.url(jdbcUrl);

      if (user != null && !user.isEmpty()) {
        dataSourceBuilder.username(user);
      }
      if (pass != null && !pass.isEmpty()) {
        dataSourceBuilder.password(pass);
      }

      return dataSourceBuilder.build();

    } catch (Exception e) {
      throw new IllegalStateException("Failed to parse DATABASE_URL: " + databaseUrl, e);
    }
  }
}
