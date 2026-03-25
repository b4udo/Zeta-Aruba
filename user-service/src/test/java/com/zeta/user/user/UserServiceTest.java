package com.zeta.user.user;

import com.zeta.user.common.UserAlreadyExistsException;
import com.zeta.user.common.UserNotFoundException;
import com.zeta.user.provisioning.ProvisioningKafkaProducer;
import com.zeta.user.user.dto.CreateUserRequest;
import com.zeta.user.user.dto.UpdateUserRequest;
import com.zeta.user.user.dto.UserResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    @Mock
    private ProvisioningKafkaProducer provisioningKafkaProducer;

    @InjectMocks
    private UserService userService;

    private User user;
    private UserResponse userResponse;
    private CreateUserRequest createRequest;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("test@zeta.local");
        user.setFirstName("Mario");
        user.setLastName("Rossi");
        user.setOrganization("Aruba S.p.A.");
        user.setRole(UserRole.USER);

        userResponse = new UserResponse();
        userResponse.setId(user.getId());
        userResponse.setEmail(user.getEmail());
        userResponse.setFirstName(user.getFirstName());
        userResponse.setLastName(user.getLastName());

        createRequest = new CreateUserRequest();
        createRequest.setEmail("test@zeta.local");
        createRequest.setFirstName("Mario");
        createRequest.setLastName("Rossi");
        createRequest.setOrganization("Aruba S.p.A.");
    }

    @Test
    void createUser_shouldSaveAndReturn() {
        when(userRepository.existsByEmail(createRequest.getEmail())).thenReturn(false);
        when(userMapper.toEntity(createRequest)).thenReturn(user);
        when(userRepository.save(user)).thenReturn(user);
        when(userMapper.toResponse(user)).thenReturn(userResponse);

        UserResponse result = userService.createUser(createRequest);

        assertThat(result.getEmail()).isEqualTo("test@zeta.local");
        verify(userRepository).save(user);
    }

    @Test
    void createUser_duplicateEmail_shouldThrow() {
        when(userRepository.existsByEmail(createRequest.getEmail())).thenReturn(true);

        assertThatThrownBy(() -> userService.createUser(createRequest))
                .isInstanceOf(UserAlreadyExistsException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void getUserById_shouldReturnUser() {
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userMapper.toResponse(user)).thenReturn(userResponse);

        UserResponse result = userService.getUserById(user.getId());

        assertThat(result.getId()).isEqualTo(user.getId());
    }

    @Test
    void getUserById_notFound_shouldThrow() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserById(id))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void getUsers_shouldReturnPage() {
        PageRequest pageable = PageRequest.of(0, 10);
        Page<User> page = new PageImpl<>(List.of(user));
        when(userRepository.findAll(pageable)).thenReturn(page);
        when(userMapper.toResponse(user)).thenReturn(userResponse);

        Page<UserResponse> result = userService.getUsers(pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void updateUser_shouldUpdateAndReturn() {
        UpdateUserRequest updateRequest = new UpdateUserRequest();
        updateRequest.setFirstName("Luigi");

        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        when(userMapper.toResponse(user)).thenReturn(userResponse);

        UserResponse result = userService.updateUser(user.getId(), updateRequest);

        verify(userMapper).updateEntity(updateRequest, user);
        verify(userRepository).save(user);
    }

    @Test
    void deleteUser_shouldDelete() {
        when(userRepository.existsById(user.getId())).thenReturn(true);

        userService.deleteUser(user.getId());

        verify(userRepository).deleteById(user.getId());
    }

    @Test
    void deleteUser_notFound_shouldThrow() {
        UUID id = UUID.randomUUID();
        when(userRepository.existsById(id)).thenReturn(false);

        assertThatThrownBy(() -> userService.deleteUser(id))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void activateService_shouldAddServiceAndPublishEvent() {
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        when(userMapper.toResponse(user)).thenReturn(userResponse);

        userService.activateService(user.getId(), "PEC");

        assertThat(user.getActivatedServices()).contains("PEC");
        verify(provisioningKafkaProducer).publishServiceActivated(user.getId(), "PEC");
    }
}
