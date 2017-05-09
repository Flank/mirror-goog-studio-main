<#import "root://activities/common/kotlin_macros.ftl" as kt>
<#if includeCppSupport!false>

    // Example of a call to a native method
    val tv = <@kt.findViewById id="R.id.sample_text" type="TextView"/>
    tv.text = stringFromJNI()
</#if>
