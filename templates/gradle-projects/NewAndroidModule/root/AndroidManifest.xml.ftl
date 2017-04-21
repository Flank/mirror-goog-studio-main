<#import "./shared_macros.ftl" as shared>
<@shared.generateManifest packageName=packageName hasApplicationBlock=(!isLibraryProject||isInstantApp) isInstantApp=isInstantApp/>
