package com.studypilot.service;

import com.studypilot.dto.ChangePasswordDTO;
import com.studypilot.dto.LoginDTO;
import com.studypilot.dto.RegisterDTO;
import com.studypilot.vo.LoginVO;
import com.studypilot.vo.UserVO;

public interface UserService {

    LoginVO register(RegisterDTO dto);

    LoginVO login(LoginDTO dto);

    void logout();

    void changePassword(ChangePasswordDTO dto);

    UserVO getCurrentUser();
}