 package com.notifyhub.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notifyhub.TestcontainersSupport;
import com.notifyhub.enums.Channel;
import com.notifyhub.enums.NotificationStatus;
import com.notifyhub.messaging.producer.NotificationEventProducer;
import com.notifyhub.notification.dto.NotificationRequestDTO;
import com.notifyhub.notification.entity.Notification;
import com.notifyhub.notification.repository.NotificationRepository;
import com.notifyhub.security.entity.AppUser;
import com.notifyhub.security.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@SpringBootTest
@ActiveProfiles("test")
@WithMockUser(username = "notifyhub", roles = "USER")
class NotificationControllerIntegrationTest extends TestcontainersSupport {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:15.8");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registerPostgresProperties(registry, POSTGRES);
    }

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    private MockMvc mockMvc;

    private AppUser currentUser;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private NotificationEventProducer notificationEventProducer;

    @BeforeEach
    void setUp() {
        currentUser = userRepository.findByUsername("notifyhub")
                .orElseGet(() -> userRepository.save(
                        new AppUser("notifyhub", "encoded-pass", "USER")
                ));

        mockMvc = webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        when(notificationEventProducer.publish(any()))
                .thenReturn(CompletableFuture.completedFuture(null));
    }

    @Test
    void shouldCreateNotification() throws Exception {
        NotificationRequestDTO request = new NotificationRequestDTO();
        request.setChannel(Channel.EMAIL);
        request.setRecipient("api-test@gmail.com");
        request.setRawSubject("API Subject");
        request.setRawBody("API body");
        request.setIdempotencyKey("api-test-" + System.currentTimeMillis());
        request.setSourceApplication("api-tests");

        mockMvc.perform(
                        post("/api/v1/notifications")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isAccepted());
    }

    @Test
    void shouldReturnNotificationById() throws Exception {
        Notification notification = new Notification();
        notification.setUser(currentUser);
        notification.setIdempotencyKey("get-test-" + System.currentTimeMillis());
        notification.setSourceApplication("api-tests");
        notification.setChannel(Channel.EMAIL);
        notification.setRecipient("get-test@gmail.com");
        notification.setTemplateCode("TEST_TEMPLATE");
        notification.setRenderedContent("Get test");
        notification.setStatus(NotificationStatus.PENDING);

        Notification saved = notificationRepository.save(notification);

        try {
            mockMvc.perform(
                            get("/api/v1/notifications/{id}", saved.getId())
                    )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(saved.getId()))
                    .andExpect(jsonPath("$.channel").value("EMAIL"))
                    .andExpect(jsonPath("$.recipient").value("get-test@gmail.com"));
        } finally {
            notificationRepository.deleteById(saved.getId());
        }
    }

    @Test
    void shouldFilterNotifications() throws Exception {
        mockMvc.perform(
                        get("/api/v1/notifications")
                                .param("status", "PENDING")
                                .param("channel", "EMAIL")
                )
                .andExpect(status().isOk());
    }

    @Test
    void shouldReturnBadRequestForInvalidStatus() throws Exception {
        mockMvc.perform(
                        get("/api/v1/notifications")
                                .param("status", "WRONG")
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void shouldFilterHistoryWithAllFiltersAndPagination() throws Exception {
        Notification n1 = new Notification();
        n1.setUser(currentUser);
        n1.setIdempotencyKey("history-1-" + System.nanoTime());
        n1.setSourceApplication("api-tests");
        n1.setChannel(Channel.EMAIL);
        n1.setRecipient("history@gmail.com");
        n1.setRenderedContent("History 1");
        n1.setStatus(NotificationStatus.SENT);

        Notification n2 = new Notification();
        n2.setUser(currentUser);
        n2.setIdempotencyKey("history-2-" + System.nanoTime());
        n2.setSourceApplication("api-tests");
        n2.setChannel(Channel.EMAIL);
        n2.setRecipient("history@gmail.com");
        n2.setRenderedContent("History 2");
        n2.setStatus(NotificationStatus.SENT);

        Notification n3 = new Notification();
        n3.setUser(currentUser);
        n3.setIdempotencyKey("history-3-" + System.nanoTime());
        n3.setSourceApplication("api-tests");
        n3.setChannel(Channel.SMS);
        n3.setRecipient("other@gmail.com");
        n3.setRenderedContent("History 3");
        n3.setStatus(NotificationStatus.FAILED);

        notificationRepository.save(n1);
        notificationRepository.save(n2);
        notificationRepository.save(n3);

        try {
            mockMvc.perform(
                            get("/api/v1/notifications")
                                    .param("recipient", "history@gmail.com")
                                    .param("status", "SENT")
                                    .param("channel", "EMAIL")
                                    .param("fromDate", "2020-01-01T00:00:00")
                                    .param("toDate", "2030-01-01T00:00:00")
                                    .param("page", "0")
                                    .param("size", "1")
                    )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.totalElements").value(2))
                    .andExpect(jsonPath("$.size").value(1))
                    .andExpect(jsonPath("$.number").value(0));
        } finally {
            notificationRepository.deleteAll(List.of(n1, n2, n3));
        }
    }

    @Test
    void shouldCreateRawNotification() throws Exception {
        NotificationRequestDTO request = new NotificationRequestDTO();
        request.setChannel(Channel.EMAIL);
        request.setRecipient("raw-api@gmail.com");
        request.setRawSubject("Raw Subject");
        request.setRawBody("Raw body");
        request.setIdempotencyKey("raw-api-" + System.nanoTime());
        request.setSourceApplication("api-tests");

        mockMvc.perform(
                        post("/api/v1/notifications")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isAccepted());
    }

    @Test
    void shouldRejectRawContentWithTemplate() throws Exception {
        NotificationRequestDTO request = new NotificationRequestDTO();
        request.setChannel(Channel.EMAIL);
        request.setRecipient("raw-api@gmail.com");
        request.setRawSubject("Raw Subject");
        request.setRawBody("Raw body");
        request.setTemplateCode("WELCOME_EMAIL");
        request.setIdempotencyKey("raw-api-" + System.nanoTime());
        request.setSourceApplication("api-tests");

        mockMvc.perform(
                        post("/api/v1/notifications")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithAnonymousUser
    void shouldRejectUnauthenticatedRequest() throws Exception {
        mockMvc.perform(
                        get("/api/v1/notifications")
                )
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "notifyhub", roles = "ADMIN")
    void shouldRejectUserWithoutRequiredRole() throws Exception {
        mockMvc.perform(
                        get("/api/v1/notifications")
                )
                .andExpect(status().isForbidden());
    }
}