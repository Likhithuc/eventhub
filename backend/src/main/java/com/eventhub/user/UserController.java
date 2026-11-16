package com.eventhub.user;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.eventhub.security.CurrentUser;
import com.eventhub.user.dto.UserResponse;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public UserResponse me(CurrentUser currentUser) {
        return UserResponse.from(userService.getActiveUser(currentUser.id()));
    }
}
