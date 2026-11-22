package com.ordermanagement.user.api;

import java.net.URI;
import java.util.UUID;

import jakarta.validation.Valid;

import com.ordermanagement.shared.common.paging.PageRequests;
import com.ordermanagement.shared.common.paging.PageResponse;
import com.ordermanagement.user.application.UserService;
import com.ordermanagement.user.application.UserView;
import com.ordermanagement.user.domain.UserStatus;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    public ResponseEntity<UserView> create(@Valid @RequestBody CreateUserRequest request) {
        UserView created = userService.create(request.email(), request.password(), request.fullName());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{id}")
    public UserView get(@PathVariable("id") UUID id) {
        return userService.get(id);
    }

    @GetMapping
    public PageResponse<UserView> list(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "status", required = false) UserStatus status,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "" + PageRequests.DEFAULT_SIZE) int size) {
        return userService.list(q, status, page, size);
    }

    @PutMapping("/{id}")
    public UserView update(@PathVariable("id") UUID id, @Valid @RequestBody UpdateUserRequest request) {
        return userService.updateProfile(id, request.fullName());
    }

    /** Xóa mềm: chuyển user sang DISABLED. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disable(@PathVariable("id") UUID id) {
        userService.disable(id);
    }

    @PostMapping("/{id}/enable")
    public UserView enable(@PathVariable("id") UUID id) {
        return userService.enable(id);
    }
}
