package com.android.repository.impl.generated.v2;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;

/**
 * DO NOT EDIT This file was generated by xjc from repo-common-02.xsd. Any changes will be lost upon
 * recompilation of the schema. See the schema file for instructions on running xjc.
 *
 * <p>A list of patches.
 *
 * <p>Java class for patchesType complex type.
 *
 * <p>The following schema fragment specifies the expected content contained within this class.
 *
 * <pre>
 * &lt;complexType name="patchesType"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence maxOccurs="unbounded"&gt;
 *         &lt;element name="patch" type="{http://schemas.android.com/repository/android/common/02}patchType"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(
        name = "patchesType",
        propOrder = {"patch"})
@SuppressWarnings({"override", "unchecked"})
public class PatchesType extends com.android.repository.impl.meta.Archive.PatchesType {

    @XmlElement(required = true)
    protected List<com.android.repository.impl.generated.v2.PatchType> patch;

    /**
     * Gets the value of the patch property.
     *
     * <p>This accessor method returns a reference to the live list, not a snapshot. Therefore any
     * modification you make to the returned list will be present inside the JAXB object. This is
     * why there is not a <CODE>set</CODE> method for the patch property.
     *
     * <p>For example, to add a new item, do as follows:
     *
     * <pre>
     *    getPatch().add(newItem);
     * </pre>
     *
     * <p>Objects of the following type(s) are allowed in the list {@link
     * com.android.repository.impl.generated.v2.PatchType }
     */
    public List<com.android.repository.impl.generated.v2.PatchType> getPatchInternal() {
        if (patch == null) {
            patch = new ArrayList<com.android.repository.impl.generated.v2.PatchType>();
        }
        return this.patch;
    }

    public List<com.android.repository.impl.meta.Archive.PatchType> getPatch() {
        return ((List) getPatchInternal());
    }

    public ObjectFactory createFactory() {
        return new ObjectFactory();
    }
}
