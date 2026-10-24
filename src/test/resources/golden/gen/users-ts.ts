/**
 * 사용자
 *
 * 테이블: public.users
 */
export interface Users {
  userId: number;
  /** 로그인 아이디 · UNIQUE */
  loginId: string;
  /** 이메일 */
  email: string | null;
  /** 사용자명 */
  userName: string;
  useYn: string;
}
