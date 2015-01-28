/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.jboss.as.clustering.jgroups.protocol;

import java.util.LinkedList;
import java.util.List;
import org.jboss.as.clustering.jgroups.logging.JGroupsLogger;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.server.ServerEnvironment;
import org.jboss.as.server.ServerEnvironmentService;
import org.jboss.as.server.mgmt.domain.HostControllerClient;
import org.jboss.as.server.mgmt.domain.HostControllerConnectionService;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
import org.jgroups.stack.IpAddress;
import org.wildfly.clustering.jgroups.spi.service.ProtocolStackServiceName;

/**
 *
 * @author Dominik Pospisil <dpospisi@redhat.com>
 */
public class DPingConfigurationService implements DPingConfigurator, Service<DPingConfigurator> {


    private static final Logger.Level DEBUG_LEVEL  = Logger.Level.DEBUG;

    private static final ServiceName SERVICE_NAME = ServiceName.JBOSS.append(ProtocolStackServiceName.CHANNEL_FACTORY.getServiceName("dping"));

    private static DPingConfigurationService instance;

    public static void install(ServiceTarget target) {
        if (instance != null) return;
        DPingConfigurationService service = new DPingConfigurationService();
        ServiceBuilder<DPingConfigurator> builder = target.addService(SERVICE_NAME, service)

                .addDependency(ServerEnvironmentService.SERVICE_NAME, ServerEnvironment.class, serverEnvironmentInjector)
                .addDependency(HostControllerConnectionService.SERVICE_NAME, HostControllerClient.class, hostControllerClientInjector);


        instance = service;
        builder.install();
    }

    public static DPingConfigurator getConfigurator() {
        return instance;
    }

    private volatile DPingConfigurator configurator;

    private static final InjectedValue<ServerEnvironment> serverEnvironmentInjector = new InjectedValue<>();
    private static final InjectedValue<HostControllerClient> hostControllerClientInjector = new InjectedValue<>();

    @Override
    public void start(StartContext sc) throws StartException {
    }

    @Override
    public void stop(StopContext sc) {
    }

    @Override
    public DPingConfigurator getValue() throws IllegalStateException, IllegalArgumentException {
        return this;
    }

    private DPingConfigurationService() {
    }

    @Override
    public List<IpAddress> getServers() {

        List<IpAddress> serverList = new LinkedList<IpAddress>();

        try {
            ModelNode op = new ModelNode();
            op.get(ModelDescriptionConstants.OP).set(ModelDescriptionConstants.READ_RESOURCE_OPERATION);

            ServerEnvironment serverEnvironment = serverEnvironmentInjector.getValue();
            String currentControllerHost = serverEnvironment.getHostControllerName();
            String currentServerName = serverEnvironment.getServerName();

            final HostControllerClient hcc = hostControllerClientInjector.getValue();
            ModelNode domainWideModel = assertSuccess(hcc.queryDomainMasterModel(op));
            ModelNode hosts = domainWideModel.get(ModelDescriptionConstants.HOST);

            // determine server group of the current server
            op = new ModelNode();
            op.get(ModelDescriptionConstants.OP).set(ModelDescriptionConstants.READ_ATTRIBUTE_OPERATION);
            op.get(ModelDescriptionConstants.ADDRESS).add(ModelDescriptionConstants.HOST);
            op.get(ModelDescriptionConstants.ADDRESS).add(currentControllerHost);
            op.get(ModelDescriptionConstants.ADDRESS).add(ModelDescriptionConstants.SERVER_CONFIG);
            op.get(ModelDescriptionConstants.ADDRESS).add(currentServerName);
            op.get(ModelDescriptionConstants.NAME).set(ModelDescriptionConstants.GROUP);

            String currentServerGroup = assertSuccess(hcc.queryDomainMasterModel(op)).asString();

            JGroupsLogger.ROOT_LOGGER.log(DEBUG_LEVEL, "DPING currentServerGroup:" + currentServerGroup);

            for (String hostName : hosts.keys()) {

                op = new ModelNode();
                op.get(ModelDescriptionConstants.OP).set(ModelDescriptionConstants.READ_RESOURCE_OPERATION);
                op.get(ModelDescriptionConstants.ADDRESS).add(ModelDescriptionConstants.HOST);
                op.get(ModelDescriptionConstants.ADDRESS).add(hostName);

                ModelNode host = assertSuccess(hcc.queryDomainMasterModel(op));
                ModelNode serverConfigs = host.get(ModelDescriptionConstants.SERVER_CONFIG);

                JGroupsLogger.ROOT_LOGGER.log(DEBUG_LEVEL, "DPING serverConfigs:" + serverConfigs);

                for (String serverName : serverConfigs.keys()) {

                    op = new ModelNode();
                    op.get(ModelDescriptionConstants.OP).set(ModelDescriptionConstants.READ_RESOURCE_OPERATION);
                    op.get(ModelDescriptionConstants.ADDRESS).add(ModelDescriptionConstants.HOST);
                    op.get(ModelDescriptionConstants.ADDRESS).add(hostName);
                    op.get(ModelDescriptionConstants.ADDRESS).add(ModelDescriptionConstants.SERVER_CONFIG);
                    op.get(ModelDescriptionConstants.ADDRESS).add(serverName);
                    ModelNode serverConfig = assertSuccess(hcc.queryDomainMasterModel(op));

                    int portOffset = serverConfig.get(ModelDescriptionConstants.SOCKET_BINDING_PORT_OFFSET).asInt(0);

                    op = new ModelNode();
                    op.get(ModelDescriptionConstants.OP).set(ModelDescriptionConstants.READ_RESOURCE_OPERATION);
                    op.get(ModelDescriptionConstants.ADDRESS).add(ModelDescriptionConstants.HOST);
                    op.get(ModelDescriptionConstants.ADDRESS).add(hostName);
                    op.get(ModelDescriptionConstants.ADDRESS).add(ModelDescriptionConstants.SERVER);
                    op.get(ModelDescriptionConstants.ADDRESS).add(serverName);
                    ModelNode server = assertSuccess(hcc.queryDomainMasterModel(op));

                    JGroupsLogger.ROOT_LOGGER.log(DEBUG_LEVEL, "DPING server:" + server);

                    // resolve socket binding group
                    String bindingGroup = null;
                    if (serverConfig.get(ModelDescriptionConstants.SOCKET_BINDING_GROUP).isDefined()) {
                        bindingGroup = serverConfig.get(ModelDescriptionConstants.SOCKET_BINDING_GROUP).asString();
                    } else {
                        op = new ModelNode();
                        op.get(ModelDescriptionConstants.OP).set(ModelDescriptionConstants.READ_RESOURCE_OPERATION);
                        op.get(ModelDescriptionConstants.ADDRESS).add(ModelDescriptionConstants.SERVER_GROUP);
                        op.get(ModelDescriptionConstants.ADDRESS).add(currentServerGroup);
                        ModelNode serverGroup = assertSuccess(hcc.queryDomainMasterModel(op));
                        bindingGroup = serverGroup.get(ModelDescriptionConstants.SOCKET_BINDING_GROUP).asString();
                    }

                    String ifaceName = "public"; // TODO resolve correct interface
                    String binding = "jgroups-tcp"; // TODO resolve correct binding

                    // resolve socket binding group name

                    // resolve socket binding
                    op = new ModelNode();
                    op.get(ModelDescriptionConstants.OP).set(ModelDescriptionConstants.READ_RESOURCE_OPERATION);
                    op.get(ModelDescriptionConstants.ADDRESS).add(ModelDescriptionConstants.SOCKET_BINDING_GROUP);
                    op.get(ModelDescriptionConstants.ADDRESS).add(bindingGroup);
                    op.get(ModelDescriptionConstants.ADDRESS).add(ModelDescriptionConstants.SOCKET_BINDING);
                    op.get(ModelDescriptionConstants.ADDRESS).add(binding);
                    ModelNode socketBinding = assertSuccess(hcc.queryDomainMasterModel(op));

                    if (socketBinding.get(ModelDescriptionConstants.INTERFACE).isDefined())
                        ifaceName = socketBinding.get(ModelDescriptionConstants.INTERFACE).asString();
                    int basePort = socketBinding.get(ModelDescriptionConstants.PORT).asInt();

                    String serverGroup = server.get(ModelDescriptionConstants.SERVER_GROUP).asString();
                    if (! currentServerGroup.equals(serverGroup)) continue;

                    if (! server.get(ModelDescriptionConstants.INTERFACE).has(ifaceName)) continue;

                    op = new ModelNode();
                    op.get(ModelDescriptionConstants.OP).set(ModelDescriptionConstants.READ_RESOURCE_OPERATION);
                    op.get(ModelDescriptionConstants.INCLUDE_RUNTIME).set(true);
                    op.get(ModelDescriptionConstants.ADDRESS).add(ModelDescriptionConstants.HOST);
                    op.get(ModelDescriptionConstants.ADDRESS).add(hostName);
                    op.get(ModelDescriptionConstants.ADDRESS).add(ModelDescriptionConstants.SERVER);
                    op.get(ModelDescriptionConstants.ADDRESS).add(serverName);
                    op.get(ModelDescriptionConstants.ADDRESS).add(ModelDescriptionConstants.INTERFACE);
                    op.get(ModelDescriptionConstants.ADDRESS).add("public"); // TODO: determine correct interface

                    ModelNode iface = assertSuccess(hcc.queryDomainMasterModel(op));
                    String inetAddr = iface.get("resolved-address").asString();

                    JGroupsLogger.ROOT_LOGGER.log(DEBUG_LEVEL, "DPING inet addr:" + inetAddr);

                    serverList.add(new IpAddress(inetAddr, basePort + portOffset));
                }

            }
            JGroupsLogger.ROOT_LOGGER.log(Logger.Level.INFO, "DPING server list:" + serverList.toString());
            return serverList;
        } catch (Exception e) {
            return new LinkedList<IpAddress>();
        }

    }

    private ModelNode assertSuccess(ModelNode response) throws Exception {
        if (ModelDescriptionConstants.SUCCESS.equals(response.get(ModelDescriptionConstants.OUTCOME).asString())) {
            return response.get(ModelDescriptionConstants.RESULT);
        }
        throw new Exception("Could not read master model:" + response.toString());
    }

}
