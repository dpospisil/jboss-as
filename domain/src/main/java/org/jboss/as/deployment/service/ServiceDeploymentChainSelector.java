/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2010, Red Hat, Inc., and individual contributors
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

package org.jboss.as.deployment.service;

import org.jboss.as.deployment.chain.DeploymentChainProvider;
import org.jboss.vfs.VirtualFile;

/**
 * Deployment chain selector which determines whether the service deployment chain should handle this deployment.
 *
 * @author John E. Bailey
 */
public class ServiceDeploymentChainSelector implements DeploymentChainProvider.Selector {
    private static final String SERVICE_ARCHIVE_EXTENSION = ".sar";
    private static final String SERVICE_LOADER_PATH = "META-INF/services/" + ServiceDeployment.class.getName();
    private static final String SERVICE_DESCRIPTOR_PATH = "META-INF/jboss-service.xml";

    @Override
    public boolean supports(final VirtualFile virtualFile) {
        return virtualFile.getName().endsWith(SERVICE_ARCHIVE_EXTENSION) || virtualFile.getChild(SERVICE_LOADER_PATH).exists() || virtualFile.getChild(SERVICE_DESCRIPTOR_PATH).exists();
    }
}
