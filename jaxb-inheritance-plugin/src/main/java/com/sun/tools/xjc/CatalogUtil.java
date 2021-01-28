package com.sun.tools.xjc;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import javax.xml.catalog.CatalogFeatures;
import javax.xml.catalog.CatalogManager;
import javax.xml.catalog.CatalogResolver;
import org.xml.sax.EntityResolver;

/**
 * The default CatalogResolver thows NPE if systemId is null, even though that should be supported,
 * and in fact is by the code if the NPE check is removed. This is a workaround to replace null with
 * an empty string.
 *
 * <p>See https://stackoverflow.com/questions/64538512
 */
public class CatalogUtil {

    static EntityResolver getCatalog(
            EntityResolver entityResolver, File catalogFile, ArrayList<URI> catalogUrls)
            throws IOException {
        if (entityResolver != null) {
            return entityResolver;
        }
        CatalogResolver resolver =
                CatalogManager.catalogResolver(
                        CatalogFeatures.builder().build(), catalogUrls.toArray(URI[]::new));
        return (publicId, systemId) ->
                resolver.resolveEntity(publicId, systemId == null ? "" : systemId);
    }
}
