-- DDL 생성 — 원본 oracle → mssql, 표 2

CREATE TABLE HR.TB_EMP_HIST (
    EMP_NO INT NOT NULL,
    START_DATE DATETIME2 NOT NULL,
    JOB_ID NVARCHAR(10) NOT NULL,
    DEPT_NM NVARCHAR(30),
    SALARY DECIMAL(8,2),
    CLASS NVARCHAR(5),
    NOTE NVARCHAR(MAX),
    CONSTRAINT PK_EMP_HIST PRIMARY KEY (EMP_NO, START_DATE)
);

CREATE TABLE HR.TB_EMP_LVL (
    LVL_ID BIGINT NOT NULL,
    EMP_NO INT NOT NULL,
    START_DATE DATETIME2 NOT NULL,
    [LEVEL] INT DEFAULT 0,
    USE_AT NCHAR(1) DEFAULT 'Y' NOT NULL,
    REG_DT DATETIME2 DEFAULT GETDATE(),
    REG_YEAR NVARCHAR(4),
    RATE DECIMAL(38,10),
    DEPT_ID INT,
    CONSTRAINT PK_TB_EMP_LVL PRIMARY KEY (LVL_ID)
);
ALTER TABLE HR.TB_EMP_LVL ADD CONSTRAINT UK_LVL UNIQUE (EMP_NO, [LEVEL]);
CREATE INDEX IX_LVL_REG ON HR.TB_EMP_LVL (REG_DT);

-- 외래 키
ALTER TABLE HR.TB_EMP_LVL ADD CONSTRAINT FK_LVL_HIST FOREIGN KEY (EMP_NO, START_DATE) REFERENCES HR.TB_EMP_HIST (EMP_NO, START_DATE);
-- [건너뜀] FK_LVL_DEPT → TB_DEPT (대상에 없는 표)

-- 코멘트
EXEC sys.sp_addextendedproperty @name=N'MS_Description', @value=N'사원 이력', @level0type=N'SCHEMA', @level0name=N'HR', @level1type=N'TABLE', @level1name=N'TB_EMP_HIST';
EXEC sys.sp_addextendedproperty @name=N'MS_Description', @value=N'사원 번호', @level0type=N'SCHEMA', @level0name=N'HR', @level1type=N'TABLE', @level1name=N'TB_EMP_HIST', @level2type=N'COLUMN', @level2name=N'EMP_NO';
EXEC sys.sp_addextendedproperty @name=N'MS_Description', @value=N'시작일', @level0type=N'SCHEMA', @level0name=N'HR', @level1type=N'TABLE', @level1name=N'TB_EMP_HIST', @level2type=N'COLUMN', @level2name=N'START_DATE';
EXEC sys.sp_addextendedproperty @name=N'MS_Description', @value=N'직무', @level0type=N'SCHEMA', @level0name=N'HR', @level1type=N'TABLE', @level1name=N'TB_EMP_HIST', @level2type=N'COLUMN', @level2name=N'JOB_ID';
EXEC sys.sp_addextendedproperty @name=N'MS_Description', @value=N'급여', @level0type=N'SCHEMA', @level0name=N'HR', @level1type=N'TABLE', @level1name=N'TB_EMP_HIST', @level2type=N'COLUMN', @level2name=N'SALARY';
EXEC sys.sp_addextendedproperty @name=N'MS_Description', @value=N'등급', @level0type=N'SCHEMA', @level0name=N'HR', @level1type=N'TABLE', @level1name=N'TB_EMP_HIST', @level2type=N'COLUMN', @level2name=N'CLASS';
EXEC sys.sp_addextendedproperty @name=N'MS_Description', @value=N'사원 등급''s', @level0type=N'SCHEMA', @level0name=N'HR', @level1type=N'TABLE', @level1name=N'TB_EMP_LVL';
EXEC sys.sp_addextendedproperty @name=N'MS_Description', @value=N'등급 ID', @level0type=N'SCHEMA', @level0name=N'HR', @level1type=N'TABLE', @level1name=N'TB_EMP_LVL', @level2type=N'COLUMN', @level2name=N'LVL_ID';
EXEC sys.sp_addextendedproperty @name=N'MS_Description', @value=N'사원 번호', @level0type=N'SCHEMA', @level0name=N'HR', @level1type=N'TABLE', @level1name=N'TB_EMP_LVL', @level2type=N'COLUMN', @level2name=N'EMP_NO';
EXEC sys.sp_addextendedproperty @name=N'MS_Description', @value=N'레벨', @level0type=N'SCHEMA', @level0name=N'HR', @level1type=N'TABLE', @level1name=N'TB_EMP_LVL', @level2type=N'COLUMN', @level2name=N'LEVEL';
EXEC sys.sp_addextendedproperty @name=N'MS_Description', @value=N'사용 여부', @level0type=N'SCHEMA', @level0name=N'HR', @level1type=N'TABLE', @level1name=N'TB_EMP_LVL', @level2type=N'COLUMN', @level2name=N'USE_AT';
