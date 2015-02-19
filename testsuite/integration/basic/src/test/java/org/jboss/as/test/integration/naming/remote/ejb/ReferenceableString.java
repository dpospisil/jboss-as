/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jboss.as.test.integration.naming.remote.ejb;

import java.io.Serializable;
import javax.naming.NamingException;
import javax.naming.Reference;
import javax.naming.Referenceable;
import javax.naming.StringRefAddr;  

/**
 *
 * @author dpospisi
 */
public class ReferenceableString implements Referenceable, Serializable {

    public static final String ADDR_TYPE = "STR";

    private String value;

    public ReferenceableString(String value) {
        this.value = value;
    }

    @Override
    public Reference getReference() throws NamingException {
        return new Reference(ReferenceableString.class.getName(), new StringRefAddr(ADDR_TYPE, value), ReferenceableStringFactory.class.getName(), null);
    }

    /**
     * @return the value
     */
    public String getValue() {
        return value;
    }

}
