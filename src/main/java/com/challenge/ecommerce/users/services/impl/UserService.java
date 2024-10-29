package com.challenge.ecommerce.users.services.impl;

import com.challenge.ecommerce.authentication.controllers.dtos.AuthenticationRequest;
import com.challenge.ecommerce.authentication.controllers.dtos.AuthenticationResponse;
import com.challenge.ecommerce.authentication.services.IAuthenticationService;
import com.challenge.ecommerce.exceptionHandlers.CustomRuntimeException;
import com.challenge.ecommerce.exceptionHandlers.ErrorCode;
import com.challenge.ecommerce.users.controllers.dtos.*;
import com.challenge.ecommerce.users.mappers.IUserMapper;
import com.challenge.ecommerce.users.repositories.UserRepository;
import com.challenge.ecommerce.users.services.IUserServices;
import com.challenge.ecommerce.utils.ApiResponse;
import com.challenge.ecommerce.utils.AuthUtils;
import com.challenge.ecommerce.utils.enums.ResponseStatus;
import com.challenge.ecommerce.utils.enums.Role;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.StringJoiner;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserService implements IUserServices {
  UserRepository userRepository;
  IUserMapper userMapper;
  IAuthenticationService authenticationService;
  PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(10);

  // user register account
  @Override
  public ApiResponse<AuthenticationResponse> signUp(UserCreateRequest userCreateRequest) {
    checkEmailUnique(userCreateRequest.getEmail());
    checkPasswordConfirm(userCreateRequest.getPassword(), userCreateRequest.getConfirmPassword());
    var user = userMapper.userCreateDtoToEntity(userCreateRequest);
    user.setPassword(passwordEncoder.encode(userCreateRequest.getPassword()));
    // create name account with UUID random
    user.setName("user_" + UUID.randomUUID().toString().substring(0, 8));
    user.setRole(Role.USER);
    userRepository.save(user);
    var auth =
        AuthenticationRequest.builder()
            .email(user.getEmail())
            .password(userCreateRequest.getPassword())
            .build();
    var resp = authenticationService.authenticate(auth);
    resp.setMessage(ResponseStatus.SUCCESS_SIGNUP.getMessage());
    return resp;
  }

  @Override
  public ApiResponse<Void> adminSignUp(AdminCreateUserRequest adminCreateUserRequest) {
    checkEmailUnique(adminCreateUserRequest.getEmail());
    checkPasswordConfirm(
        adminCreateUserRequest.getNewPassword(), adminCreateUserRequest.getConfirmPassword());
    var user = userMapper.adminCreateUserDtoToEntity(adminCreateUserRequest);
    user.setPassword(passwordEncoder.encode(adminCreateUserRequest.getNewPassword()));
    user.setName("user_" + UUID.randomUUID().toString().substring(0, 8));
    if (adminCreateUserRequest.getRole().equals(Role.ADMIN.toString())) {
      user.setRole(Role.ADMIN);
    } else {
      user.setRole(Role.USER);
    }
    return ApiResponse.<Void>builder().message(ResponseStatus.SUCCESS_SIGNUP.getMessage()).build();
  }

  @Override
  @Transactional
  public ApiResponse<Void> updateUserDetail(UserUpdateRequest userUpdateRequest) {
    if (userUpdateRequest.getName() != null) checkNameUnique(userUpdateRequest.getName());
    if (userUpdateRequest.getEmail() != null) checkEmailUnique(userUpdateRequest.getEmail());
    var oldUser =
        userRepository
            .findByEmail(AuthUtils.getUserCurrent())
            .orElseThrow(() -> new CustomRuntimeException(ErrorCode.USER_NOT_FOUND));
    var user = userMapper.userUpdateDtoToEntity(oldUser, userUpdateRequest);
    // check update password when password not null .
    if (userUpdateRequest.getOldPassword() != null) {
      if (!passwordEncoder.matches(userUpdateRequest.getOldPassword(), oldUser.getPassword())) {
        throw new CustomRuntimeException(ErrorCode.PASSWORD_INCORRECT);
      }
      if (userUpdateRequest.getNewPassword() == null) {
        throw new CustomRuntimeException(ErrorCode.NEW_PASSWORD_CANNOT_BE_NULL);
      }
      if (userUpdateRequest.getConfirmPassword() == null) {
        throw new CustomRuntimeException(ErrorCode.CONFIRM_PASSWORD_CANNOT_BE_NULL);
      }
      if (userUpdateRequest.getNewPassword().equals(userUpdateRequest.getOldPassword())) {
        throw new CustomRuntimeException(ErrorCode.PASSWORD_SHOULD_NOT_MATCH_OLD);
      }
      checkPasswordConfirm(
          userUpdateRequest.getNewPassword(), userUpdateRequest.getConfirmPassword());
      user.setPassword(passwordEncoder.encode(userUpdateRequest.getNewPassword()));
    }
    userRepository.save(user);
    return ApiResponse.<Void>builder().message(ResponseStatus.SUCCESS_UPDATE.getMessage()).build();
  }

  @Override
  public ApiResponse<UserGetResponse> getMe() {
    var user =
        userRepository
            .findByEmail(AuthUtils.getUserCurrent())
            .orElseThrow(() -> new CustomRuntimeException(ErrorCode.UNAUTHENTICATED));

    return ApiResponse.<UserGetResponse>builder()
        .result(userMapper.userEntityToUserGetResponse(user))
        .build();
  }

  @Override
  @Transactional
  public ApiResponse<Void> adminUpdateUserDetail(
      AdminUpdateUserRequest adminUpdateUserRequest, String userId) {
    if (adminUpdateUserRequest.getName() != null) checkNameUnique(adminUpdateUserRequest.getName());
    if (adminUpdateUserRequest.getEmail() != null)
      checkEmailUnique(adminUpdateUserRequest.getEmail());
    var oldUser =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new CustomRuntimeException(ErrorCode.USER_NOT_FOUND));
    // check update password when password not null .
    if (adminUpdateUserRequest.getNewPassword() != null) {
      if (adminUpdateUserRequest.getConfirmPassword() == null)
        throw new CustomRuntimeException(ErrorCode.CONFIRM_PASSWORD_CANNOT_BE_NULL);
      checkPasswordConfirm(
          adminUpdateUserRequest.getNewPassword(), adminUpdateUserRequest.getConfirmPassword());
      oldUser.setPassword(passwordEncoder.encode(adminUpdateUserRequest.getNewPassword()));
    }
    var newUser = userMapper.adminUpdateUserDtoToEntity(oldUser, adminUpdateUserRequest);
    newUser.setRole(Role.USER);
    if (adminUpdateUserRequest.getRole() != null) {
      if (adminUpdateUserRequest.getRole().equals(Role.ADMIN.toString())) {
        newUser.setRole(Role.ADMIN);
      }
    }
    userRepository.save(newUser);
    return ApiResponse.<Void>builder().message(ResponseStatus.SUCCESS_UPDATE.getMessage()).build();
  }

  @Override
  @Transactional
  public ApiResponse<Void> adminDeleteUser(AdminDeleteUserRequest adminDeleteUserRequest) {
    StringJoiner joiner = new StringJoiner(" ");
    for (String id : adminDeleteUserRequest.getIds()) {
      var user =
          userRepository
              .findById(id)
              .orElseThrow(() -> new CustomRuntimeException(ErrorCode.USER_NOT_FOUND));
      user.setDeletedAt(LocalDateTime.now());
      userRepository.save(user);
      joiner.add(user.getName());
    }
    return ApiResponse.<Void>builder()
        .message(ResponseStatus.SUCCESS_DELETE.getFormattedMessage(joiner.toString()))
        .build();
  }

  // check email unique .
  private void checkEmailUnique(String email) {
    if (userRepository.existsByEmail(email)) {
      throw new CustomRuntimeException(ErrorCode.EMAIL_EXISTED);
    }
  }

  // check user name unique .
  private void checkNameUnique(String name) {
    if (userRepository.existsByName(name)) {
      throw new CustomRuntimeException(ErrorCode.USERNAME_ALREADY_EXISTS);
    }
  }

  // check password confirm exactly .
  private void checkPasswordConfirm(String newPassword, String confirmPassword) {
    if (!newPassword.equals(confirmPassword)) {
      throw new CustomRuntimeException(ErrorCode.CONFIRM_PASSWORD_NOT_MATCH);
    }
  }
}
