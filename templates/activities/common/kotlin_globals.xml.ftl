<globals>
    <#assign generateKotlin=(((includeKotlinSupport!false) || (language!'Java')?string == 'Kotlin'))>

    <global id="generateKotlin" type="boolean" value="${generateKotlin?string}" />
    <!-- Indicates whether the extension of the file is kt or java -->
    <global id="ktOrJavaExt" type="string" value="${generateKotlin?string('kt','java')}" />
</globals>