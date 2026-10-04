package com.ex.shop.repo;

import com.ex.shop.domain.Member;
import com.example.custom.Query;

/** 바탕 사슬의 타입 변수 → Member · 다른 패키지의 @Query 는 안 본다(이름 규칙) */
public interface MemberRepository extends BaseRepo<Member, Long> {

    @Query("anything")
    Member findTop1ByIdGreaterThan(Long id);
}
