<#if includeCppSupport!false>

    // Example of a call to a native method
    val tv = findViewById(R.id.sample_text) as TextView
    tv.text = stringFromJNI()
</#if>
