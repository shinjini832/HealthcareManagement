package com.healthcare.management.service;

import com.healthcare.management.model.Appointment;
import com.healthcare.management.model.User;
import com.healthcare.management.model.UserOAuthToken;
import com.healthcare.management.repository.UserOAuthTokenRepository;
import com.healthcare.management.repository.UserRepository;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.api.client.util.DateTime;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleCalendarService {

    private final UserOAuthTokenRepository userOAuthTokenRepository;
    private final UserRepository userRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${google.client-id}")
    private String clientId;

    @Value("${google.client-secret}")
    private String clientSecret;

    @Value("${google.redirect-uri}")
    private String redirectUri;

    public String getAuthorizationUrl(String email) {
        return UriComponentsBuilder.fromUriString("https://accounts.google.com/o/oauth2/v2/auth")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", "https://www.googleapis.com/auth/calendar")
                .queryParam("access_type", "offline")
                .queryParam("prompt", "consent")
                .queryParam("state", email)
                .build()
                .toUriString();
    }

    public void saveTokenFromCode(String code, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found with email: " + email));

        Map<String, String> requestParams = new HashMap<>();
        requestParams.put("client_id", clientId);
        requestParams.put("client_secret", clientSecret);
        requestParams.put("code", code);
        requestParams.put("redirect_uri", redirectUri);
        requestParams.put("grant_type", "authorization_code");

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "https://oauth2.googleapis.com/token", requestParams, Map.class
        );

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            Map<String, Object> body = response.getBody();
            String accessToken = (String) body.get("access_token");
            String refreshToken = (String) body.get("refresh_token");
            Number expiresIn = (Number) body.get("expires_in");

            LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(expiresIn.longValue());

            Optional<UserOAuthToken> existingTokenOpt = userOAuthTokenRepository.findByUserEmail(email);
            UserOAuthToken token;
            if (existingTokenOpt.isPresent()) {
                token = existingTokenOpt.get();
                token.setAccessToken(accessToken);
                if (refreshToken != null) {
                    token.setRefreshToken(refreshToken);
                }
                token.setExpiresAt(expiresAt);
            } else {
                token = UserOAuthToken.builder()
                        .user(user)
                        .accessToken(accessToken)
                        .refreshToken(refreshToken)
                        .expiresAt(expiresAt)
                        .build();
            }
            userOAuthTokenRepository.save(token);
        } else {
            throw new IllegalStateException("Failed to exchange auth code for tokens with Google");
        }
    }

    private String getValidAccessToken(UserOAuthToken token) {
        if (LocalDateTime.now().isBefore(token.getExpiresAt().minusMinutes(1))) {
            return token.getAccessToken();
        }

        if (token.getRefreshToken() == null) {
            throw new IllegalStateException("Access token is expired and refresh token is unavailable");
        }

        log.info("Refreshing expired Google access token for user: {}", token.getUser().getEmail());

        Map<String, String> requestParams = new HashMap<>();
        requestParams.put("client_id", clientId);
        requestParams.put("client_secret", clientSecret);
        requestParams.put("refresh_token", token.getRefreshToken());
        requestParams.put("grant_type", "refresh_token");

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "https://oauth2.googleapis.com/token", requestParams, Map.class
        );

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            Map<String, Object> body = response.getBody();
            String newAccessToken = (String) body.get("access_token");
            Number expiresIn = (Number) body.get("expires_in");
            token.setAccessToken(newAccessToken);
            token.setExpiresAt(LocalDateTime.now().plusSeconds(expiresIn.longValue()));
            userOAuthTokenRepository.save(token);
            return newAccessToken;
        } else {
            throw new IllegalStateException("Failed to refresh access token with Google");
        }
    }

    private Calendar getCalendarService(UserOAuthToken token) {
        String accessToken = getValidAccessToken(token);
        GoogleCredentials credentials = GoogleCredentials.create(new AccessToken(accessToken, null));
        return new Calendar.Builder(
                new NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                new HttpCredentialsAdapter(credentials)
        )
        .setApplicationName("Healthcare-Management")
        .build();
    }

    public void createEvent(Appointment appointment) {
        // Create event for patient if connected
        Optional<UserOAuthToken> patientTokenOpt = userOAuthTokenRepository.findByUserEmail(appointment.getPatient().getEmail());
        if (patientTokenOpt.isPresent()) {
            try {
                Calendar patientService = getCalendarService(patientTokenOpt.get());
                Event event = buildCalendarEvent(appointment);
                Event createdEvent = patientService.events().insert("primary", event).execute();
                appointment.setGoogleEventIdPatient(createdEvent.getId());
                log.info("Created Google Calendar event for patient: {}", appointment.getPatient().getEmail());
            } catch (Exception e) {
                log.error("Failed to create Google Calendar event for patient: {}", appointment.getPatient().getEmail(), e);
            }
        }

        // Create event for doctor if connected
        Optional<UserOAuthToken> doctorTokenOpt = userOAuthTokenRepository.findByUserEmail(appointment.getDoctor().getUser().getEmail());
        if (doctorTokenOpt.isPresent()) {
            try {
                Calendar doctorService = getCalendarService(doctorTokenOpt.get());
                Event event = buildCalendarEvent(appointment);
                Event createdEvent = doctorService.events().insert("primary", event).execute();
                appointment.setGoogleEventIdDoctor(createdEvent.getId());
                log.info("Created Google Calendar event for doctor: {}", appointment.getDoctor().getUser().getEmail());
            } catch (Exception e) {
                log.error("Failed to create Google Calendar event for doctor: {}", appointment.getDoctor().getUser().getEmail(), e);
            }
        }
    }

    public void cancelEvent(Appointment appointment) {
        // Delete event for patient if connected and exists
        if (appointment.getGoogleEventIdPatient() != null) {
            Optional<UserOAuthToken> patientTokenOpt = userOAuthTokenRepository.findByUserEmail(appointment.getPatient().getEmail());
            if (patientTokenOpt.isPresent()) {
                try {
                    Calendar patientService = getCalendarService(patientTokenOpt.get());
                    patientService.events().delete("primary", appointment.getGoogleEventIdPatient()).execute();
                    log.info("Deleted Google Calendar event for patient: {}", appointment.getPatient().getEmail());
                } catch (Exception e) {
                    log.error("Failed to delete Google Calendar event for patient: {}", appointment.getPatient().getEmail(), e);
                }
            }
        }

        // Delete event for doctor if connected and exists
        if (appointment.getGoogleEventIdDoctor() != null) {
            Optional<UserOAuthToken> doctorTokenOpt = userOAuthTokenRepository.findByUserEmail(appointment.getDoctor().getUser().getEmail());
            if (doctorTokenOpt.isPresent()) {
                try {
                    Calendar doctorService = getCalendarService(doctorTokenOpt.get());
                    doctorService.events().delete("primary", appointment.getGoogleEventIdDoctor()).execute();
                    log.info("Deleted Google Calendar event for doctor: {}", appointment.getDoctor().getUser().getEmail());
                } catch (Exception e) {
                    log.error("Failed to delete Google Calendar event for doctor: {}", appointment.getDoctor().getUser().getEmail(), e);
                }
            }
        }
    }

    private Event buildCalendarEvent(Appointment appointment) {
        Event event = new Event()
                .setSummary("Healthcare Appointment - " + appointment.getDoctor().getUser().getFullName())
                .setDescription("Urgency Level: " + appointment.getUrgencyLevel() + "\nSymptoms: " + appointment.getPatientSymptoms())
                .setLocation("Healthcare Clinic");

        ZoneId defaultZone = ZoneId.systemDefault();

        ZonedDateTime startZoned = LocalDateTime.of(appointment.getAppointmentDate(), appointment.getStartTime()).atZone(defaultZone);
        ZonedDateTime endZoned = LocalDateTime.of(appointment.getAppointmentDate(), appointment.getEndTime()).atZone(defaultZone);

        DateTime startDateTime = new DateTime(Date.from(startZoned.toInstant()));
        EventDateTime start = new EventDateTime().setDateTime(startDateTime);
        event.setStart(start);

        DateTime endDateTime = new DateTime(Date.from(endZoned.toInstant()));
        EventDateTime end = new EventDateTime().setDateTime(endDateTime);
        event.setEnd(end);

        return event;
    }
}
