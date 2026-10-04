package com.ex.e;

import com.ex.c.*;
import org.springframework.data.jpa.repository.JpaRepository;

/** 와일드카드 import 라 UserMaster 둘(mod-c·mod-d)을 import·패키지·모듈로 못 가른다 — 둘 다 같은 표라 하나로 본다(6-17) */
public interface UserRepository extends JpaRepository<UserMaster, String> {
}
