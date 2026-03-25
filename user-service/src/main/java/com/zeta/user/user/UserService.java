package com.zeta.user.user;

import com.zeta.user.common.UserAlreadyExistsException;
import com.zeta.user.common.UserNotFoundException;
import com.zeta.user.provisioning.ProvisioningKafkaProducer;
import com.zeta.user.user.dto.CreateUserRequest;
import com.zeta.user.user.dto.UpdateUserRequest;
import com.zeta.user.user.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final ProvisioningKafkaProducer provisioningKafkaProducer;

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new UserAlreadyExistsException("User with email " + request.getEmail() + " already exists");
        }
        User user = userMapper.toEntity(request);
        User saved = userRepository.save(user);
        log.info("User created: {}", saved.getId());
        return userMapper.toResponse(saved);
    }

    public UserResponse getUserById(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + id));
        return userMapper.toResponse(user);
    }

    public Page<UserResponse> getUsers(Pageable pageable) {
        return userRepository.findAll(pageable).map(userMapper::toResponse);
    }

    @Transactional
    public UserResponse updateUser(UUID id, UpdateUserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + id));
        userMapper.updateEntity(request, user);
        User saved = userRepository.save(user);
        log.info("User updated: {}", saved.getId());
        return userMapper.toResponse(saved);
    }

    @Transactional
    public void deleteUser(UUID id) {
        if (!userRepository.existsById(id)) {
            throw new UserNotFoundException("User not found with id: " + id);
        }
        userRepository.deleteById(id);
        log.info("User deleted: {}", id);
    }

    @Transactional
    public UserResponse activateService(UUID userId, String serviceName) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + userId));
        user.getActivatedServices().add(serviceName.toUpperCase());
        User saved = userRepository.save(user);
        provisioningKafkaProducer.publishServiceActivated(userId, serviceName.toUpperCase());
        log.info("Service {} activated for user {}", serviceName, userId);
        return userMapper.toResponse(saved);
    }
}
