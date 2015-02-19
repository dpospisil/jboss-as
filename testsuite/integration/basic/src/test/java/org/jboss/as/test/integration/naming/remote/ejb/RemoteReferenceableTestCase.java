/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jboss.as.test.integration.naming.remote.ejb;

import java.util.Properties;
import javax.naming.Context;
import javax.naming.InitialContext;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.junit.InSequence;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.as.arquillian.container.ManagementClient;
import org.jboss.as.naming.JndiPermission;
import org.jboss.as.test.shared.integration.ejb.security.CallbackHandler;
import static org.jboss.as.test.shared.integration.ejb.security.PermissionUtils.createPermissionsXmlAsset;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 *
 * @author dpospisi
 */
@RunWith(Arquillian.class)
public class RemoteReferenceableTestCase {

    private static final String ARCHIVE_NAME = "test";
    
    @ArquillianResource
    private ManagementClient managementClient;
    
    @Deployment
    public static Archive<?> deploy() {
        final JavaArchive jar = ShrinkWrap.create(JavaArchive.class, ARCHIVE_NAME + ".jar");
        jar.addClasses(RemoteReferenceableTestCase.class, ReferenceableString.class, ReferenceableStringFactory.class);
        return jar;
    }    
    
    public InitialContext getRemoteContext() throws Exception {
        final Properties env = new Properties();
        env.put(Context.INITIAL_CONTEXT_FACTORY, org.jboss.naming.remote.client.InitialContextFactory.class.getName());
        env.put(Context.PROVIDER_URL, managementClient.getRemoteEjbURL().toString());
        env.put("jboss.naming.client.ejb.context", true);
        env.put("jboss.naming.client.connect.options.org.xnio.Options.SASL_POLICY_NOPLAINTEXT", "false");
        env.put("jboss.naming.client.security.callback.handler.class", CallbackHandler.class.getName());
        return new InitialContext(env);
    }
    

    @Test
    @InSequence(1)
    public void testReferenceable() throws Exception {
        ReferenceableString refStr = new ReferenceableString("test");
        InitialContext ctx = new InitialContext();
        ctx.bind("java:jboss/exported/refStr", refStr);
        Object o = ctx.lookup("java:jboss/exported/refStr");
        Assert.assertTrue("Wrong type: " + o.getClass().getName(), o instanceof ReferenceableString);        
    }
    @Test
    @RunAsClient
    @InSequence(2)
    public void testRemoteReferenceable() throws Exception {
        ReferenceableString refStr = new ReferenceableString("test");
        InitialContext ctx = getRemoteContext();
        Object o = ctx.lookup("refStr");
        Assert.assertTrue("Wrong type: " + o.getClass().getName(), o instanceof ReferenceableString);
    }
    @Test
    @InSequence(3)
    public void unbindReferenceable() throws Exception {
        ReferenceableString refStr = new ReferenceableString("test");
        InitialContext ctx = new InitialContext();
        ctx.unbind("java:jboss/exported/refStr");
    }
    
}
