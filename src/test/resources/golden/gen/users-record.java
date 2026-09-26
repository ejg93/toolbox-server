package com.example.dto;

/**
 * 사용자
 *
 * <p>테이블: public.users</p>
 * @param loginId 로그인 아이디
 * @param email 이메일
 * @param userName 사용자명
 */
public record Users(
        Long userId,
        String loginId,
        String email,
        String userName,
        String useYn
) {
}
