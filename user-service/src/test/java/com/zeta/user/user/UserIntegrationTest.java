package com.zeta.user.user;

import com.zeta.user.user.dto.CreateUserRequest;
import com.zeta.user.user.dto.UpdateUserRequest;
import com.zeta.user.user.dto.UserResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class UserIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("zeta_users_test")
            .withUsername("test")
            .withPassword("test");

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Test
    void fullCrudFlow() {
        // Create
        CreateUserRequest createReq = new CreateUserRequest();
        createReq.setEmail("integration@zeta.local");
        createReq.setFirstName("Test");
        createReq.setLastName("User");
        createReq.setOrganization("Aruba S.p.A.");
        createReq.setRole(UserRole.USER);

        UserResponse created = userService.createUser(createReq);
        assertThat(created.getId()).isNotNull();
        assertThat(created.getEmail()).isEqualTo("integration@zeta.local");

        // Read
        UserResponse found = userService.getUserById(created.getId());
        assertThat(found.getFirstName()).isEqualTo("Test");

        // Update
        UpdateUserRequest updateReq = new UpdateUserRequest();
        updateReq.setFirstName("Updated");
        UserResponse updated = userService.updateUser(created.getId(), updateReq);
        assertThat(updated.getFirstName()).isEqualTo("Updated");

        // Delete
        userService.deleteUser(created.getId());
        assertThat(userRepository.findById(created.getId())).isEmpty();
    }
}
