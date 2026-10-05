<?xml version="1.0" encoding="[=encoding!'UTF-8']"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<!-- [=comment]([=table]) - Table → Spring 소스 생성([=dialect]) -->
<mapper namespace="[=Name]">

    <resultMap id="[=name]Map" type="[=packageName].service.[=Name]VO">
<#list fields as f>
        <<#if f.pk>id<#else>result</#if> property="[=f.name]" column="[=f.column]"/>
</#list>
    </resultMap>

    <sql id="cols">
        <#list fields as f>[=f.column]<#sep>, </#list>
    </sql>

    <sql id="search">
<#if searchField??>
        <if test="searchKeyword != null and searchKeyword != ''">
<#if dialect == "mariadb">
            AND [=searchField.column] LIKE CONCAT('%', #{searchKeyword}, '%')
<#elseif dialect == "mssql">
            AND [=searchField.column] LIKE '%' + #{searchKeyword} + '%'
<#else>
            AND [=searchField.column] LIKE '%' || #{searchKeyword} || '%'
</#if>
        </if>
</#if>
    </sql>

    <select id="select[=Name]List" parameterType="[=packageName].service.[=Name]VO" resultMap="[=name]Map">
<#if dialect == "oracle" || dialect == "tibero">
        SELECT * FROM ( SELECT ROWNUM RNUM, ALL_LIST.* FROM (
            SELECT <include refid="cols"/>
            FROM [=table]
            <where>
                <include refid="search"/>
            </where>
            ORDER BY <#list pk as p>[=p.column]<#sep>, </#list>
        ) ALL_LIST )
        WHERE RNUM &gt; #{firstIndex} AND RNUM &lt;= #{firstIndex} + #{recordCountPerPage}
<#elseif dialect == "mssql">
        SELECT <include refid="cols"/>
        FROM [=table]
        <where>
            <include refid="search"/>
        </where>
        ORDER BY <#list pk as p>[=p.column]<#sep>, </#list>
        OFFSET #{firstIndex} ROWS FETCH NEXT #{recordCountPerPage} ROWS ONLY
<#else>
        SELECT <include refid="cols"/>
        FROM [=table]
        <where>
            <include refid="search"/>
        </where>
        ORDER BY <#list pk as p>[=p.column]<#sep>, </#list>
        LIMIT #{recordCountPerPage} OFFSET #{firstIndex}
</#if>
    </select>

    <select id="select[=Name]ListTotCnt" parameterType="[=packageName].service.[=Name]VO" resultType="int">
        SELECT COUNT(*) FROM [=table]
        <where>
            <include refid="search"/>
        </where>
    </select>

    <select id="select[=Name]Detail" parameterType="[=packageName].service.[=Name]VO" resultMap="[=name]Map">
        SELECT <include refid="cols"/>
        FROM [=table]
        WHERE <#list pk as p>[=p.column] = #{[=p.name]}<#sep> AND </#list>
    </select>

    <insert id="insert[=Name]" parameterType="[=packageName].service.[=Name]VO">
        INSERT INTO [=table] (<#list fields as f>[=f.column]<#sep>, </#list>)
        VALUES (<#list fields as f>#{[=f.name]}<#sep>, </#list>)
    </insert>

    <update id="update[=Name]" parameterType="[=packageName].service.[=Name]VO">
        UPDATE [=table]
        SET <#if nonPk?has_content><#list nonPk as f>[=f.column] = #{[=f.name]}<#sep>, </#list><#else><#list pk as p>[=p.column] = #{[=p.name]}<#sep>, </#list></#if>
        WHERE <#list pk as p>[=p.column] = #{[=p.name]}<#sep> AND </#list>
    </update>

    <delete id="delete[=Name]" parameterType="[=packageName].service.[=Name]VO">
        DELETE FROM [=table]
        WHERE <#list pk as p>[=p.column] = #{[=p.name]}<#sep> AND </#list>
    </delete>
</mapper>
