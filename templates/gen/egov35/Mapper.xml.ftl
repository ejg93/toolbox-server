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
<#list search as f>
<#if f.query == "LIKE">
        <if test="[=f.name] != null<#if f.string> and [=f.name] != ''</#if>">
<#if dialect == "mariadb">
            AND [=f.column] LIKE CONCAT('%', #{[=f.name]}, '%')
<#elseif dialect == "mssql">
            AND [=f.column] LIKE '%' + #{[=f.name]} + '%'
<#else>
            AND [=f.column] LIKE '%' || #{[=f.name]} || '%'
</#if>
        </if>
<#elseif f.query == "EQ">
        <if test="[=f.name] != null<#if f.string> and [=f.name] != ''</#if>">
            AND [=f.column] = #{[=f.name]}
        </if>
<#elseif f.query == "BETWEEN">
        <if test="[=f.name]From != null and [=f.name]From != ''">
            AND [=f.column] &gt;= <@day f.name + "From"/>
        </if>
        <if test="[=f.name]To != null and [=f.name]To != ''">
            AND [=f.column] &lt; <@day f.name + "To" true/>
        </if>
</#if>
</#list>
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
        INSERT INTO [=table] (<#list insertCols as f>[=f.column]<#sep>, </#list>)
        VALUES (<#list insertCols as f><#if f.now>[=f.nowExpr]<#else>#{[=f.name]}</#if><#sep>, </#list>)
    </insert>

    <update id="update[=Name]" parameterType="[=packageName].service.[=Name]VO">
        UPDATE [=table]
        SET <#if updateCols?has_content><#list updateCols as f>[=f.column] = <#if f.now>[=f.nowExpr]<#else>#{[=f.name]}</#if><#sep>, </#list><#else><#list pk as p>[=p.column] = #{[=p.name]}<#sep>, </#list></#if>
        WHERE <#list pk as p>[=p.column] = #{[=p.name]}<#sep> AND </#list>
    </update>

    <delete id="delete[=Name]" parameterType="[=packageName].service.[=Name]VO">
        DELETE FROM [=table]
        WHERE <#list pk as p>[=p.column] = #{[=p.name]}<#sep> AND </#list>
    </delete>
</mapper>
<#-- 7-17 — yyyy-MM-dd 글 → 방언 날짜 식. next 면 그다음 날(끝 날짜를 포함하게 「&lt; 다음 날」) -->
<#macro day name next=false><#if dialect == "mariadb">STR_TO_DATE(#{[=name]}, '%Y-%m-%d')<#if next> + INTERVAL 1 DAY</#if><#elseif dialect == "mssql"><#if next>DATEADD(DAY, 1, CONVERT(DATE, #{[=name]}, 23))<#else>CONVERT(DATE, #{[=name]}, 23)</#if><#else>TO_DATE(#{[=name]}, 'YYYY-MM-DD')<#if next> + 1</#if></#if></#macro>
