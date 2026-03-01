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
@CrossOrigin(origins = { "http://localhost:3000", "https://linkdin-devloper-link.vercel.app" })
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
   * Step 2: Fetch user profile from LinkedIn using the access token
   * (server-side).
   * This avoids CORS issues since the browser can't call LinkedIn APIs directly.
   * Also saves/updates the user in the database.
   */
  @PostMapping("/linkedin/userinfo")
  public ResponseEntity<?> fetchAndSaveUser(@RequestBody Map<String, String> request) {
    String accessToken = request.get("access_token");
    if (accessToken == null || accessToken.isBlank()) {
      return ResponseEntity.badRequest().body(Map.of("error", "Access token is missing"));
    }

    try {
      RestTemplate restTemplate = new RestTemplate();

      // Fetch profile from LinkedIn API (server-side, no CORS issue)
      HttpHeaders headers = new HttpHeaders();
      headers.setBearerAuth(accessToken);
      HttpEntity<String> entity = new HttpEntity<>(headers);

      @SuppressWarnings("unchecked")
      ResponseEntity<Map<String, Object>> profileResponse = restTemplate.exchange(
          "https://api.linkedin.com/v2/userinfo",
          HttpMethod.GET,
          entity,
          (Class<Map<String, Object>>) (Class<?>) Map.class);

      Map<String, Object> profile = profileResponse.getBody();
      if (profile == null) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(Map.of("error", "Failed to fetch profile from LinkedIn"));
      }

      // Build user from LinkedIn profile
      User user = new User();
      user.setFirstName((String) profile.getOrDefault("given_name", ""));
      user.setLastName((String) profile.getOrDefault("family_name", ""));
      user.setEmail((String) profile.getOrDefault("email", ""));
      user.setProfilePicture((String) profile.getOrDefault("picture", ""));
      user.setLinkedinId((String) profile.getOrDefault("sub", ""));

      // Save or update in database
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
          .body(Map.of("error", "Failed to fetch/save user: " + e.getMessage()));
    }
  }

  /**
   * Legacy: Save user details sent directly from frontend.
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
