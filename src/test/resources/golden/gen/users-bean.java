package com.example.dto;

/**
 * 사용자
 *
 * <p>테이블: public.users</p>
 */
public class Users {

    private Long userId;

    /** 로그인 아이디 */
    private String loginId;

    /** 이메일 */
    private String email;

    /** 사용자명 */
    private String userName;

    private String useYn;

    public Users() {
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getLoginId() {
        return loginId;
    }

    public void setLoginId(String loginId) {
        this.loginId = loginId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getUseYn() {
        return useYn;
    }

    public void setUseYn(String useYn) {
        this.useYn = useYn;
    }
}
