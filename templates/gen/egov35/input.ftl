<#-- 7-17 — 등록·수정 화면 입력 칸 하나(설계 52 §3-4). f.html 로 꼴을 고른다. select·radio·checkbox 에는 required 를 안 붙인다 -->
<#macro input f>
<#local req><#if f.required> required="required"</#if></#local>
<#if f.html == "textarea">
<form:textarea path="[=f.name]" id="[=f.name]" title="[=f.comment]" rows="5"[=req]/><#t>
<#elseif f.html == "number">
<form:input path="[=f.name]" id="[=f.name]" title="[=f.comment]" type="number"<#if f.step> step="any"</#if>[=req]/><#t>
<#elseif f.html == "date">
<form:input path="[=f.name]" id="[=f.name]" title="[=f.comment]" type="date"[=req]/><#t>
<#elseif f.html == "datetime">
<form:input path="[=f.name]" id="[=f.name]" title="[=f.comment]" type="datetime-local"[=req]/><#t>
<#elseif f.html == "time">
<form:input path="[=f.name]" id="[=f.name]" title="[=f.comment]" type="time"[=req]/><#t>
<#elseif f.html == "select">
<form:select path="[=f.name]" id="[=f.name]" title="[=f.comment]"><form:option value="">선택</form:option><#list f.options as v><form:option value="[=v]">[=v]</form:option></#list></form:select><#t>
<#elseif f.html == "radio">
<#list f.options as v><form:radiobutton path="[=f.name]" id="[=f.name]_[=v]" value="[=v]"/><label for="[=f.name]_[=v]">[=v]</label><#sep> </#list><#t>
<#elseif f.html == "checkbox">
<form:checkbox path="[=f.name]" id="[=f.name]"/><#t>
<#else>
<form:input path="[=f.name]" id="[=f.name]" title="[=f.comment]"<#if f.string && (f.length > 0)> maxlength="[=f.length?c]"</#if>[=req]/><#t>
</#if>
</#macro>
<#-- 칸 머리 — radio 는 보기마다 label 이 있어 머리는 글만 -->
<#macro head f><#if f.html == "radio">[=f.comment]<#else><label for="[=f.name]">[=f.comment]</label></#if></#macro>
