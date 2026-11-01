package kr.go.hr.comtnbbs.service;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 게시물 VO(검색·페이징 포함)
 *
 * <pre>
 * << 개정이력(Modification Information) >>
 *
 *   수정일      수정자          수정내용
 *  -------    --------    ---------------------------
 *   (생성)     toolbox      Table → Spring 소스 생성(egovframework.rte)
 * </pre>
 */
public class ComtnbbsVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 게시물 ID */
    private BigDecimal nttId;

    /** 게시판 ID */
    private String bbsId;

    /** 게시물 제목 */
    private String nttSj;

    /** 게시물 내용 */
    private String nttCn;

    /** 사용 여부 */
    private String useAt;

    /** 게시 시작일 */
    private LocalDateTime ntceBgnde;

    /** 조회 수 */
    private Long rdcnt;

    /** 문장 구분 */
    private String sntncSe;

    /** 첨부 파일 */
    private byte[] atchFile;

    /** 최초 등록 시점 */
    private LocalDateTime frstRegistPnttm;

    /** 최초 등록자 ID */
    private String frstRegisterId;

    /** 최종 수정 시점 */
    private LocalDateTime lastUpdtPnttm;

    /** 최종 수정자 ID */
    private String lastUpdusrId;

    /** 게시 시작일 검색 시작(yyyy-MM-dd) */
    private String ntceBgndeFrom;

    /** 게시 시작일 검색 끝(yyyy-MM-dd, 그날 포함) */
    private String ntceBgndeTo;

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

    public BigDecimal getNttId() {
        return nttId;
    }

    public void setNttId(BigDecimal nttId) {
        this.nttId = nttId;
    }

    public String getBbsId() {
        return bbsId;
    }

    public void setBbsId(String bbsId) {
        this.bbsId = bbsId;
    }

    public String getNttSj() {
        return nttSj;
    }

    public void setNttSj(String nttSj) {
        this.nttSj = nttSj;
    }

    public String getNttCn() {
        return nttCn;
    }

    public void setNttCn(String nttCn) {
        this.nttCn = nttCn;
    }

    public String getUseAt() {
        return useAt;
    }

    public void setUseAt(String useAt) {
        this.useAt = useAt;
    }

    public LocalDateTime getNtceBgnde() {
        return ntceBgnde;
    }

    public void setNtceBgnde(LocalDateTime ntceBgnde) {
        this.ntceBgnde = ntceBgnde;
    }

    public Long getRdcnt() {
        return rdcnt;
    }

    public void setRdcnt(Long rdcnt) {
        this.rdcnt = rdcnt;
    }

    public String getSntncSe() {
        return sntncSe;
    }

    public void setSntncSe(String sntncSe) {
        this.sntncSe = sntncSe;
    }

    public byte[] getAtchFile() {
        return atchFile;
    }

    public void setAtchFile(byte[] atchFile) {
        this.atchFile = atchFile;
    }

    public LocalDateTime getFrstRegistPnttm() {
        return frstRegistPnttm;
    }

    public void setFrstRegistPnttm(LocalDateTime frstRegistPnttm) {
        this.frstRegistPnttm = frstRegistPnttm;
    }

    public String getFrstRegisterId() {
        return frstRegisterId;
    }

    public void setFrstRegisterId(String frstRegisterId) {
        this.frstRegisterId = frstRegisterId;
    }

    public LocalDateTime getLastUpdtPnttm() {
        return lastUpdtPnttm;
    }

    public void setLastUpdtPnttm(LocalDateTime lastUpdtPnttm) {
        this.lastUpdtPnttm = lastUpdtPnttm;
    }

    public String getLastUpdusrId() {
        return lastUpdusrId;
    }

    public void setLastUpdusrId(String lastUpdusrId) {
        this.lastUpdusrId = lastUpdusrId;
    }

    public String getNtceBgndeFrom() {
        return ntceBgndeFrom;
    }

    public void setNtceBgndeFrom(String ntceBgndeFrom) {
        this.ntceBgndeFrom = ntceBgndeFrom;
    }

    public String getNtceBgndeTo() {
        return ntceBgndeTo;
    }

    public void setNtceBgndeTo(String ntceBgndeTo) {
        this.ntceBgndeTo = ntceBgndeTo;
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
