package com.onconavigator.web;

import com.onconavigator.domain.NotificationPreference;
import com.onconavigator.domain.enums.NotificationChannel;
import com.onconavigator.notification.NotificationPreferenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link NotificationPreferenceController}.
 *
 * <p>Tests verify:
 * <ul>
 *   <li>GET /api/notification-preferences returns user prefs, falls back to admin defaults</li>
 *   <li>PUT /api/notification-preferences sets userId from JWT subject (prevents escalation)</li>
 *   <li>GET /api/notification-preferences/defaults delegates to service</li>
 *   <li>PUT /api/notification-preferences/defaults sets userId=null, adminDefault=true</li>
 * </ul>
 *
 * <p>Direct method invocation (not MockMvc) due to Testcontainers/Docker context issues.
 */
@ExtendWith(MockitoExtension.class)
class NotificationPreferenceControllerTest {

    @Mock
    private NotificationPreferenceService preferenceService;

    private NotificationPreferenceController controller;

    private static final UUID USER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @BeforeEach
    void setUp() {
        controller = new NotificationPreferenceController(preferenceService);
    }

    private JwtAuthenticationToken createJwt(UUID subject) {
        Jwt jwt = new Jwt(
                "token-value",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "RS256"),
                Map.of("sub", subject.toString())
        );
        return new JwtAuthenticationToken(jwt);
    }

    // ---- GET /api/notification-preferences ----

    @Test
    void getMyPreferences_returnsUserPrefs_whenUserPrefsExist() {
        NotificationPreference userPref = new NotificationPreference();
        userPref.setUserId(USER_ID);
        userPref.setChannel(NotificationChannel.TEAMS);
        userPref.setEnabled(true);

        when(preferenceService.getUserPreferences(USER_ID)).thenReturn(List.of(userPref));

        ResponseEntity<List<NotificationPreference>> response =
                controller.getMyPreferences(createJwt(USER_ID));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).getUserId()).isEqualTo(USER_ID);
    }

    @Test
    void getMyPreferences_fallsBackToAdminDefaults_whenNoUserPrefs() {
        NotificationPreference adminDefault = new NotificationPreference();
        adminDefault.setAdminDefault(true);
        adminDefault.setChannel(NotificationChannel.TEAMS);
        adminDefault.setEnabled(true);

        when(preferenceService.getUserPreferences(USER_ID)).thenReturn(List.of());
        when(preferenceService.getAdminDefaults()).thenReturn(List.of(adminDefault));

        ResponseEntity<List<NotificationPreference>> response =
                controller.getMyPreferences(createJwt(USER_ID));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).isAdminDefault()).isTrue();
    }

    // ---- PUT /api/notification-preferences ----

    @Test
    void updateMyPreference_setsUserIdFromJwt_preventingEscalation() {
        NotificationPreference incomingPref = new NotificationPreference();
        // Attacker tries to set a different userId in the body
        incomingPref.setUserId(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"));
        incomingPref.setChannel(NotificationChannel.EMAIL);
        incomingPref.setAdminDefault(true); // Attacker tries to set admin

        when(preferenceService.save(any(NotificationPreference.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<NotificationPreference> response =
                controller.updateMyPreference(incomingPref, createJwt(USER_ID));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        NotificationPreference saved = response.getBody();
        // Controller must override userId from JWT and force adminDefault=false
        assertThat(saved.getUserId()).isEqualTo(USER_ID);
        assertThat(saved.isAdminDefault()).isFalse();
    }

    // ---- GET /api/notification-preferences/defaults ----

    @Test
    void getAdminDefaults_delegatesToService() {
        NotificationPreference adminPref = new NotificationPreference();
        adminPref.setAdminDefault(true);
        adminPref.setChannel(NotificationChannel.TEAMS);

        when(preferenceService.getAdminDefaults()).thenReturn(List.of(adminPref));

        ResponseEntity<List<NotificationPreference>> response = controller.getAdminDefaults();

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).hasSize(1);
        verify(preferenceService).getAdminDefaults();
    }

    // ---- PUT /api/notification-preferences/defaults ----

    @Test
    void updateAdminDefault_setsUserIdNull_andAdminDefaultTrue() {
        NotificationPreference incomingPref = new NotificationPreference();
        incomingPref.setUserId(USER_ID); // Should be overridden to null
        incomingPref.setAdminDefault(false); // Should be overridden to true
        incomingPref.setChannel(NotificationChannel.TEAMS);

        when(preferenceService.save(any(NotificationPreference.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<NotificationPreference> response =
                controller.updateAdminDefault(incomingPref);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        NotificationPreference saved = response.getBody();
        assertThat(saved.getUserId()).isNull();
        assertThat(saved.isAdminDefault()).isTrue();
    }
}
