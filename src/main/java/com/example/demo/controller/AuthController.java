package com.example.demo.controller;

import com.example.demo.model.User;
import com.example.demo.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "http://localhost:3000")
public class AuthController {

  @Value("${linkedin.client.id}")
  private String clientId;

  @Value("${linkedin.client.secret}")
  private String clientSecret;

  @Value("${linkedin.redirect.uri}")
  private String redirectUri;

  @Autowired
  private UserRepository userRepository;

  /**
   * Step 1: Exchange the authorization code for an access token.
   * The client_secret is kept secure on the backend — never exposed to the
   * frontend.
   * Returns the access_token to the frontend so it can fetch user details
   * directly.
   */
  @PostMapping("/linkedin/token")
  public ResponseEntity<?> exchangeCodeForToken(@RequestBody Map<String, String> request) {
    String code = request.get("code");
    if (code == null || code.isBlank()) {
      return ResponseEntity.badRequest().body(Map.of("error", "Authorization code is missing"));
    }

    RestTemplate restTemplate = new RestTemplate();
    String tokenUrl = "https://www.linkedin.com/oauth/v2/accessToken";

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

    MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
    params.add("grant_type", "authorization_code");
    params.add("code", code);
    params.add("redirect_uri", redirectUri);
    params.add("client_id", clientId);
    params.add("client_secret", clientSecret);

    HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(params, headers);

    try {
      @SuppressWarnings("unchecked")
      ResponseEntity<Map<String, Object>> response = restTemplate.postForEntity(tokenUrl, entity,
          (Class<Map<String, Object>>) (Class<?>) Map.class);
      Map<String, Object> body = response.getBody();

      if (body == null || !body.containsKey("access_token")) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(Map.of("error", "LinkedIn did not return an access token"));
      }

      // Return only the access_token to the frontend (keep client_secret secure)
      return ResponseEntity.ok(Map.of("access_token", body.get("access_token")));

    } catch (Exception e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(Map.of("error", "Failed to exchange code for token: " + e.getMessage()));
    }
  }

  /**
   * Step 2: Receive user details fetched by the frontend from LinkedIn APIs.
   * Stores/updates the user in the PostgreSQL database and returns the saved
   * user.
   */
  @PostMapping("/linkedin/save-user")
  public ResponseEntity<?> saveUser(@RequestBody User user) {
    if (user.getEmail() == null || user.getEmail().isBlank()) {
      return ResponseEntity.badRequest().body(Map.of("error", "Email is required"));
    }

    try {
      User savedUser = userRepository.findByEmail(user.getEmail())
          .map(existingUser -> {
            existingUser.setFirstName(user.getFirstName());
            existingUser.setLastName(user.getLastName());
            existingUser.setProfilePicture(user.getProfilePicture());
            existingUser.setLinkedinId(user.getLinkedinId());
            return userRepository.save(existingUser);
          })
          .orElseGet(() -> userRepository.save(user));

      return ResponseEntity.ok(savedUser);
    } catch (Exception e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(Map.of("error", "Failed to save user: " + e.getMessage()));
    }
  }
}
