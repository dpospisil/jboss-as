/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jboss.as.test.integration.naming.remote.ejb;

import java.util.Hashtable;
import javax.naming.Context;
import javax.naming.Name;
import javax.naming.Reference;
import javax.naming.spi.ObjectFactory;

/**
 *
 * @author dpospisi
 */
public class ReferenceableStringFactory implements ObjectFactory {

    @Override
    public Object getObjectInstance(Object obj, Name name, Context nameCtx, Hashtable<?, ?> environment) throws Exception {
        if (obj instanceof Reference) {
            Reference ref = (Reference) obj;
            String addr = (String) ref.get(ReferenceableString.ADDR_TYPE).getContent();
            return new ReferenceableString(addr);
        }
        return obj;
    }

}
