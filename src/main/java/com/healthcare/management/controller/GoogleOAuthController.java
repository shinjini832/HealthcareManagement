package com.healthcare.management.controller;

import com.healthcare.management.service.GoogleCalendarService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

import java.security.Principal;

@RestController
@RequestMapping("/api/oauth/google")
@RequiredArgsConstructor
public class GoogleOAuthController {

    private final GoogleCalendarService googleCalendarService;

    @GetMapping("/connect")
    public RedirectView connect(Principal principal) {
        String email = principal.getName();
        String authorizationUrl = googleCalendarService.getAuthorizationUrl(email);
        return new RedirectView(authorizationUrl);
    }

    @GetMapping("/callback")
    public ResponseEntity<String> callback(
            @RequestParam("code") String code,
            @RequestParam("state") String email
    ) {
        try {
            googleCalendarService.saveTokenFromCode(code, email);
            return ResponseEntity.ok("Successfully connected your Google Calendar! You can close this window now.");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to connect Google Calendar: " + e.getMessage());
        }
    }
}
