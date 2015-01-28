/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.jboss.as.clustering.jgroups.protocol;

import java.util.List;
import org.jgroups.stack.IpAddress;

/**
 *
 * @author Dominik Pospisil <dpospisi@redhat.com>
 */
public interface DPingConfigurator {

    List<IpAddress> getServers();

}
