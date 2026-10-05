package kr.go.hr.emphist.service;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 사원 이력 VO(검색·페이징 포함)
 *
 * <pre>
 * << 개정이력(Modification Information) >>
 *
 *   수정일      수정자          수정내용
 *  -------    --------    ---------------------------
 *   (생성)     toolbox      Table → Spring 소스 생성(org.egovframe.rte)
 * </pre>
 */
public class EmpHistVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 사원 번호 */
    private Integer empNo;

    /** 시작일 */
    private LocalDateTime startDate;

    /** 직무 */
    private String jobId;

    /** 부서명 */
    private String deptNm;

    /** 급여 */
    private BigDecimal salary;

    /** 등급 */
    private String class_;

    /** NOTE */
    private String note;

    /** 검색 조건 */
    private String searchCondition = "";

    /** 검색어 */
    private String searchKeyword = "";

    /** 현재 페이지 */
    private int pageIndex = 1;

    /** 페이지당 건수 */
    private int pageUnit = 10;

    /** 페이지 목록 크기 */
    private int pageSize = 10;

    /** 첫 행 위치 */
    private int firstIndex = 1;

    /** 끝 행 위치 */
    private int lastIndex = 1;

    /** 페이지당 행 수 */
    private int recordCountPerPage = 10;

    public Integer getEmpNo() {
        return empNo;
    }

    public void setEmpNo(Integer empNo) {
        this.empNo = empNo;
    }

    public LocalDateTime getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDateTime startDate) {
        this.startDate = startDate;
    }

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public String getDeptNm() {
        return deptNm;
    }

    public void setDeptNm(String deptNm) {
        this.deptNm = deptNm;
    }

    public BigDecimal getSalary() {
        return salary;
    }

    public void setSalary(BigDecimal salary) {
        this.salary = salary;
    }

    public String getClass_() {
        return class_;
    }

    public void setClass_(String class_) {
        this.class_ = class_;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getSearchCondition() {
        return searchCondition;
    }

    public void setSearchCondition(String searchCondition) {
        this.searchCondition = searchCondition;
    }

    public String getSearchKeyword() {
        return searchKeyword;
    }

    public void setSearchKeyword(String searchKeyword) {
        this.searchKeyword = searchKeyword;
    }

    public int getPageIndex() {
        return pageIndex;
    }

    public void setPageIndex(int pageIndex) {
        this.pageIndex = pageIndex;
    }

    public int getPageUnit() {
        return pageUnit;
    }

    public void setPageUnit(int pageUnit) {
        this.pageUnit = pageUnit;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public int getFirstIndex() {
        return firstIndex;
    }

    public void setFirstIndex(int firstIndex) {
        this.firstIndex = firstIndex;
    }

    public int getLastIndex() {
        return lastIndex;
    }

    public void setLastIndex(int lastIndex) {
        this.lastIndex = lastIndex;
    }

    public int getRecordCountPerPage() {
        return recordCountPerPage;
    }

    public void setRecordCountPerPage(int recordCountPerPage) {
        this.recordCountPerPage = recordCountPerPage;
    }
}
