package com.eventhub.user;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eventhub.common.exception.ApiException;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public User getActiveUser(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> ApiException.unauthorized("Account no longer exists"));
        if (!user.isActive()) {
            throw ApiException.forbidden("Account is suspended");
        }
        return user;
    }
}
