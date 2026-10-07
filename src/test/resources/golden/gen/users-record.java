package com.example.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * 사용자
 *
 * <p>테이블: public.users</p>
 * @param loginId 로그인 아이디 · UNIQUE
 * @param email 이메일
 * @param userName 사용자명
 */
public record Users(
        Long userId,
        @NotBlank
        @Size(max = 50)
        String loginId,
        @Size(max = 200)
        String email,
        @NotBlank
        @Size(max = 100)
        String userName,
        @Size(max = 1)
        String useYn
) {
}
