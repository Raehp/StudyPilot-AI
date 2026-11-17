package com.studypilot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.studypilot.common.ResultCode;
import com.studypilot.dto.LoginDTO;
import com.studypilot.dto.RegisterDTO;
import com.studypilot.entity.SysUser;
import com.studypilot.exception.BusinessException;
import com.studypilot.mapper.SysUserMapper;
import com.studypilot.security.AuthConstants;
import com.studypilot.security.JwtUtil;
import com.studypilot.security.UserContext;
import com.studypilot.service.UserService;
import com.studypilot.vo.LoginVO;
import com.studypilot.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final SysUserMapper sysUserMapper;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate stringRedisTemplate;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public LoginVO register(RegisterDTO dto) {
        Long count = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, dto.getUsername()));
        if (count > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "用户名已存在");
        }
        SysUser user = new SysUser();
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setNickname(dto.getNickname() == null || dto.getNickname().isBlank()
                ? dto.getUsername() : dto.getNickname());
        user.setStatus(1);
        sysUserMapper.insert(user);
        // 回读数据库默认值（create_time/email 等由 DB 生成）
        user = sysUserMapper.selectById(user.getId());
        return issueToken(user);
    }

    @Override
    public LoginVO login(LoginDTO dto) {
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, dto.getUsername()));
        if (user == null || !passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "用户名或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(ResultCode.FORBIDDEN, "账号已被禁用");
        }
        return issueToken(user);
    }

    @Override
    public void logout() {
        stringRedisTemplate.delete(AuthConstants.tokenKey(UserContext.getUserId()));
    }

    @Override
    public UserVO getCurrentUser() {
        SysUser user = sysUserMapper.selectById(UserContext.getUserId());
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return toVO(user);
    }

    private LoginVO issueToken(SysUser user) {
        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        stringRedisTemplate.opsForValue().set(
                AuthConstants.tokenKey(user.getId()), token,
                Duration.ofSeconds(jwtUtil.getExpireSeconds()));
        return new LoginVO(token, toVO(user));
    }

    private UserVO toVO(SysUser user) {
        UserVO vo = new UserVO();
        BeanUtils.copyProperties(user, vo);
        return vo;
    }
}