package org.jboss.as.clustering.jgroups.protocol;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jgroups.annotations.ManagedAttribute;
import org.jgroups.annotations.ManagedOperation;
import org.jgroups.annotations.Property;
import org.jgroups.util.BoundedList;
import org.jgroups.util.Responses;
import org.jgroups.util.Tuple;
import org.jgroups.Address;
import org.jgroups.Event;
import org.jgroups.Message;
import org.jgroups.PhysicalAddress;
import org.jgroups.protocols.Discovery;
import org.jgroups.protocols.PingData;
import org.jgroups.protocols.PingHeader;
import static org.jgroups.protocols.Discovery.marshal;
import org.jgroups.stack.IpAddress;


public class DPING extends Discovery {

    private static final short DPING_ID = 1001;

    /* -----------------------------------------    Properties     --------------------------------------- */

    @Property(description="Number of additional ports to be probed for membership. A port_range of 0 does not " +
      "probe additional ports. Example: initial_hosts=A[7800] port_range=0 probes A:7800, port_range=1 probes " +
      "A:7800 and A:7801")
    private int port_range=1;

    @Property(description="max number of hosts to keep beyond the ones in initial_hosts")
    protected int max_dynamic_hosts=2000;

    /* --------------------------------------------- Fields ------------------------------------------------------ */

    /**
     * List of PhysicalAddresses
     */

    /** https://jira.jboss.org/jira/browse/JGRP-989 */
    protected BoundedList<PhysicalAddress> dynamic_hosts;



    public DPING() {
        setId(DPING_ID);
    }

    public boolean isDynamic() {
        return false;
    }

    public int getPortRange() {
        return port_range;
    }

    public void setPortRange(int port_range) {
        this.port_range=port_range;
    }

    @ManagedAttribute
    public String getDynamicHostList() {
        return dynamic_hosts.toString();
    }

    @ManagedOperation
    public void clearDynamicHostList() {
        dynamic_hosts.clear();
    }

    public void init() throws Exception {
        super.init();
        dynamic_hosts=new BoundedList<PhysicalAddress>(max_dynamic_hosts);
    }

    public Object down(Event evt) {
        Object retval=super.down(evt);
        switch(evt.getType()) {
            case Event.VIEW_CHANGE:
                for(Address logical_addr: members) {
                    PhysicalAddress physical_addr=(PhysicalAddress)down_prot.down(new Event(Event.GET_PHYSICAL_ADDRESS, logical_addr));
                    if(physical_addr != null) {
                        dynamic_hosts.addIfAbsent(physical_addr);
                    }
                }
                break;
            case Event.SET_PHYSICAL_ADDRESS:
                Tuple<Address,PhysicalAddress> tuple=(Tuple<Address,PhysicalAddress>)evt.getArg();
                PhysicalAddress physical_addr=tuple.getVal2();
                if(physical_addr != null)
                    dynamic_hosts.addIfAbsent(physical_addr);
                break;
        }
        return retval;
    }

    public void discoveryRequestReceived(Address sender, String logical_name, PhysicalAddress physical_addr) {
        super.discoveryRequestReceived(sender, logical_name, physical_addr);
        if(physical_addr != null) {
            dynamic_hosts.addIfAbsent(physical_addr);
        }
    }

    @Override
    public void findMembers(List<Address> members, boolean initial_discovery, Responses responses) {
        PhysicalAddress physical_addr=(PhysicalAddress)down(new Event(Event.GET_PHYSICAL_ADDRESS, local_addr));

        // https://issues.jboss.org/browse/JGRP-1670
        PingData data=new PingData(local_addr, false, org.jgroups.util.UUID.get(local_addr), physical_addr);
        PingHeader hdr=new PingHeader(PingHeader.GET_MBRS_REQ).clusterName(cluster_name);

        Set<PhysicalAddress> cluster_members=new HashSet<PhysicalAddress>(dynamic_hosts);
        cluster_members.addAll(pingDomain());

        if(use_disk_cache) {
            // this only makes sense if we have PDC below us
            Collection<PhysicalAddress> list=(Collection<PhysicalAddress>)down_prot.down(new Event(Event.GET_PHYSICAL_ADDRESSES));
            if(list != null)
                for(PhysicalAddress phys_addr: list)
                    if(!cluster_members.contains(phys_addr))
                        cluster_members.add(phys_addr);
        }

        for(final PhysicalAddress addr: cluster_members) {
            if(physical_addr != null && addr.equals(physical_addr)) // no need to send the request to myself
                continue;
            // the message needs to be DONT_BUNDLE, see explanation above
            final Message msg=new Message(addr).setFlag(Message.Flag.INTERNAL, Message.Flag.DONT_BUNDLE, Message.Flag.OOB)
              .putHeader(this.id,hdr).setBuffer(marshal(data));
            log.trace("%s: sending discovery request to %s", local_addr, msg.getDest());
            down_prot.down(new Event(Event.MSG, msg));
        }
    }

    private List<IpAddress> pingDomain() {

        DPingConfigurator configurator = DPingConfigurationService.getConfigurator();
        return configurator.getServers();

    }
}

