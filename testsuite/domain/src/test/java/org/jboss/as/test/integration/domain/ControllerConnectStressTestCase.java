/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.as.test.integration.domain;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.net.URL;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.jboss.logging.Logger;
import org.jboss.as.test.integration.domain.management.util.DomainLifecycleUtil;
import org.jboss.as.test.integration.domain.management.util.JBossAsManagedConfiguration;
import org.jboss.sasl.util.UsernamePasswordHashUtil;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Master-slave domain connection stress test. Starts one master host and repeatedly start and stop multiple slave hosts.
 * 
 * @author <a href="dpospisi@redhat.com">Dominik Pospisil</a>
 */
public class ControllerConnectStressTestCase {
    
    private static final int SLAVES_FROM = Integer.valueOf(System.getProperty("jboss.test.ccstresscase.slavesFrom", "1"));
    private static final int SLAVES_TO = Integer.valueOf(System.getProperty("jboss.test.ccstresscase.slavesTo", "3"));
    private static final int RECONNECT_COUNT = Integer.valueOf(System.getProperty("jboss.test.ccstresscase.reconnectCount", "20"));
    
    private static DomainLifecycleUtil masterUtil;
    //private static DomainLifecycleUtil[] slaveUtils = new DomainLifecycleUtil[SLAVE_COUNT];
    
    private static String masterAddress = System.getProperty("jboss.test.host.master.address", "127.0.0.1");
    private static String slaveAddress = System.getProperty("jboss.test.host.slave.address", "127.0.0.1");       
    
    private static boolean masterOnly =  System.getProperty("jboss.test.ccstresscase.masteronly") != null;
    private static boolean slaveOnly =  System.getProperty("jboss.test.ccstresscase.slaveonly") != null;
    
    private static int MGMT_PORT = 9999;
    private static int MGMT_HTTP_PORT = 9990;
    
    private static final Logger log = Logger.getLogger(ControllerConnectStressTestCase.class);
        
    
    @BeforeClass
    public static void setupDomain() throws Exception {                
               
        if (!slaveOnly) {            
            // start master
            masterUtil = new DomainLifecycleUtil(getMasterConfiguration(SLAVES_FROM, SLAVES_TO));
            masterUtil.start();                        
        }
    }    
    
    @AfterClass
    public static void shutdownDomain() {
        
        if (!slaveOnly) {
            // stop master
            masterUtil.stop();
        }
    }    
    
    @Test
    public void testConnectController() throws Exception {
                
        if (masterOnly) {
            synchronized(this) {
                wait();
            }
            return;
        }
        
        int slavesCount = SLAVES_TO - SLAVES_FROM + 1;
        ExecutorService pool = Executors.newFixedThreadPool(slavesCount);
        Future[] futures = new Future[slavesCount];
        for (int k=0; k<slavesCount; k++) {
            final int slave = SLAVES_FROM + k;
            
            futures[k] = pool.submit(new Callable<Object>() {
                
                public Object call() throws Exception {
                    DomainLifecycleUtil slaveUtil = new DomainLifecycleUtil(getSlaveConfiguration(slave + 1));
                    Random r = new Random(System.currentTimeMillis());
                    for (int c = 0; c < RECONNECT_COUNT; c++) {
                            log.info("Starting slave " + slave + ".");
                            slaveUtil.start();
                            log.info("Slave " + slave + " started.");
                            Thread.sleep(r.nextInt(5000));
                            log.info("Stopping slave " + slave + ".");
                            slaveUtil.stop();
                            log.info("Slave " + slave + " stopped.");
                            Thread.sleep(r.nextInt(5000));
                    }            
                    return null;                    
                }
            });
        }

        for (Future f : futures) {
            f.get();
        }

    }
    
     public void run() {
        System.out.println("Hello from a thread!");
    }
     
    private static JBossAsManagedConfiguration getMasterConfiguration(int slavesFrom, int slavesTo) throws Exception {

        final String testName = ControllerConnectStressTestCase.class.getSimpleName();        
        File domains = new File("target" + File.separator + "domains" + File.separator + testName);
        final File hostDir = new File(domains, "master");
        final String hostDirPath = hostDir.getAbsolutePath();
        final File hostConfigDir = new File(hostDir, "configuration");
        hostConfigDir.mkdirs();        

        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        final JBossAsManagedConfiguration hostConfig = new JBossAsManagedConfiguration();
        hostConfig.setHostControllerManagementAddress(masterAddress);
        hostConfig.setHostCommandLineProperties("-Djboss.test.host.master.address=" + masterAddress + 
                " -Djboss.test.host.slave.address=" + slaveAddress);
        URL url = tccl.getResource("domain-configs/domain-standard.xml");
        hostConfig.setDomainConfigFile(new File(url.toURI()).getAbsolutePath());
        System.out.println(hostConfig.getDomainConfigFile());
        url = tccl.getResource("host-configs/host-master.xml");
        hostConfig.setHostConfigFile(new File(url.toURI()).getAbsolutePath());
        System.out.println(hostConfig.getHostConfigFile());
        hostConfig.setDomainDirectory(hostDir.getAbsolutePath());
        hostConfig.setHostName("master");
        hostConfig.setHostControllerManagementPort(MGMT_PORT);
        hostConfig.setStartupTimeoutInSeconds(120);
       
        File usersFile = new File(hostConfigDir, "mgmt-users.properties");
        FileOutputStream fos = new FileOutputStream(usersFile);
        PrintWriter pw = new PrintWriter(fos);
        for (int s=slavesFrom; s<=slavesTo; s++) {
            String slave = "slave" + String.valueOf(s);
            pw.println(slave + "=" + new UsernamePasswordHashUtil().generateHashedHexURP(slave, "ManagementRealm", "slave_user_password".toCharArray()));
        }
        pw.close();
        fos.close();                
        
        return hostConfig;
    }    
    
    private static JBossAsManagedConfiguration getSlaveConfiguration(int host) throws Exception {

        String slave = "slave" + String.valueOf(host);
        
        final String testName = ControllerConnectStressTestCase.class.getSimpleName();        
        File domains = new File("target" + File.separator + "domains" + File.separator + testName);
        final File hostDir = new File(domains, slave);
        final String hostDirPath = hostDir.getAbsolutePath();
        final File hostConfigDir = new File(hostDir, "configuration");
        hostConfigDir.mkdirs();        

        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        final JBossAsManagedConfiguration hostConfig = new JBossAsManagedConfiguration();
        hostConfig.setHostControllerManagementAddress(slaveAddress);
        hostConfig.setHostCommandLineProperties("-Djboss.test.host.master.address=" + masterAddress + 
                " -Djboss.test.host.slave.address=" + slaveAddress +
                " -Djboss.test.host.slave.nativeport=" + String.valueOf(MGMT_PORT - host * 10) +
                " -Djboss.test.host.slave.httpport=" + String.valueOf(MGMT_HTTP_PORT - host * 10) +
                " -Djboss.host.name=" + slave);
        URL url = tccl.getResource("domain-configs/domain-standard.xml");
        hostConfig.setDomainConfigFile(new File(url.toURI()).getAbsolutePath());
        System.out.println(hostConfig.getDomainConfigFile());
        url = tccl.getResource("host-configs/host-minimal.xml");
        hostConfig.setHostConfigFile(new File(url.toURI()).getAbsolutePath());
        System.out.println(hostConfig.getHostConfigFile());
        hostConfig.setDomainDirectory(hostDir.getAbsolutePath());
        hostConfig.setHostName(slave);
        hostConfig.setHostControllerManagementPort(MGMT_PORT - host * 10);
        hostConfig.setStartupTimeoutInSeconds(30);
        
        File usersFile = new File(hostConfigDir, "mgmt-users.properties");
        FileOutputStream fos = new FileOutputStream(usersFile);
        PrintWriter pw = new PrintWriter(fos);
        pw.println(slave + "=" + new UsernamePasswordHashUtil().generateHashedHexURP(slave, "ManagementRealm", "slave_user_password".toCharArray()));
        pw.close();
        fos.close();                

       
        return hostConfig;
    }    
    
}
