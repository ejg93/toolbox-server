package com.ex.d;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** 모듈마다 되풀이한 같은 엔티티(6-17) — 표가 같다 */
@Entity
@Table(name = "COMVNUSERMASTER")
public class UserMaster {
    private String esntlId;
}
