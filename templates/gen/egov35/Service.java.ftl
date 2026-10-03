package [=packageName].service;

import java.util.List;

<#assign what = "서비스"><#include "header.ftl">
public interface [=Name]Service {

    /** [=comment] 목록 */
    List<[=Name]VO> select[=Name]List([=Name]VO searchVO) throws Exception;

    /** [=comment] 목록 건수 */
    int select[=Name]ListTotCnt([=Name]VO searchVO) throws Exception;

    /** [=comment] 상세 */
    [=Name]VO select[=Name]Detail([=Name]VO vo) throws Exception;

    /** [=comment] 등록 */
    void insert[=Name]([=Name]VO vo) throws Exception;

    /** [=comment] 수정 */
    void update[=Name]([=Name]VO vo) throws Exception;

    /** [=comment] 삭제 */
    void delete[=Name]([=Name]VO vo) throws Exception;
}
