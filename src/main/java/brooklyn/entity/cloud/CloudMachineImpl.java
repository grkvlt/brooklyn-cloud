/*
 * Copyright 2014 by Cloudsoft Corporation Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package brooklyn.entity.cloud;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;
import io.cloudsoft.networking.portforwarding.DockerPortForwarder;
import io.cloudsoft.networking.subnet.PortForwarder;
import io.cloudsoft.networking.subnet.SubnetTier;
import io.cloudsoft.networking.subnet.SubnetTierImpl;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.jclouds.compute.domain.OsFamily;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.enricher.Enrichers;
import brooklyn.entity.Entity;
import brooklyn.entity.annotation.EffectorParam;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.BasicStartableImpl;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.basic.SoftwareProcessImpl;
import brooklyn.entity.effector.Effectors;
import brooklyn.entity.group.Cluster;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.Startable;
import brooklyn.entity.trait.StartableMethods;
import brooklyn.location.Location;
import brooklyn.location.LocationDefinition;
import brooklyn.location.MachineProvisioningLocation;
import brooklyn.location.access.PortForwardManager;
import brooklyn.location.basic.BasicLocationDefinition;
import brooklyn.location.basic.Machines;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.location.cloud.CloudMachineLocation;
import brooklyn.location.cloud.CloudLocation;
import brooklyn.location.cloud.CloudResolver;
import brooklyn.location.jclouds.JcloudsLocation;
import brooklyn.location.jclouds.JcloudsLocationConfig;
import brooklyn.location.jclouds.templates.PortableTemplateBuilder;
import brooklyn.management.LocationManager;
import brooklyn.policy.PolicySpec;
import brooklyn.policy.ha.ServiceFailureDetector;
import brooklyn.policy.ha.ServiceReplacer;
import brooklyn.policy.ha.ServiceRestarter;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.guava.Maybe;
import brooklyn.util.net.Cidr;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * @author Andrea Turli
 */
public class CloudMachineImpl extends BasicStartableImpl implements CloudMachine, Startable {

    private static final Logger log = LoggerFactory.getLogger(CloudMachineImpl.class);

    private JcloudsLocation jcloudsLocation;

    public CloudMachineImpl() {
    }

    @Override
    public void init() {
        log.info("Starting machine id {}", getId());
    }

    protected void connectSensors() {
    }

    protected void disconnectSensors() {
    }

    @Override
    public String getShortName() {
        return "Machine";
    }

    @Override
    public CloudEnvironment getInfrastructure() {
        return getConfig(DOCKER_INFRASTRUCTURE);
    }

    @Override
    public CloudMachineLocation getDynamicLocation() {
        return (CloudMachineLocation) getAttribute(DYNAMIC_LOCATION);
    }

    /**
     * Create a new {@link CloudMachineLocation} wrapping the machine we are starting in.
     */
    @Override
    public CloudMachineLocation createLocation(Map<String, ?> flags) {
        String locationSpec, locationName;
        CloudEnvironment infrastructure = getConfig(DOCKER_INFRASTRUCTURE);
        CloudLocation docker = infrastructure.getDynamicLocation();
        locationName = docker.getId() + "-" + getId();

        locationSpec = format(CloudResolver.CLOUD_MACHINE_SPEC, infrastructure.getId(), getId()) + format(":(name=\"%s\")", locationName);
        setAttribute(LOCATION_SPEC, locationSpec);
        LocationDefinition definition = new BasicLocationDefinition(locationName, locationSpec, flags);
        Location location = getManagementContext().getLocationRegistry().resolve(definition);
        getManagementContext().getLocationManager().manage(location);

        setAttribute(DYNAMIC_LOCATION, location);
        setAttribute(LOCATION_NAME, location.getId());
        if (getConfig(CloudEnvironment.REGISTER_CLOUD_MACHINE_LOCATIONS)) {
            getManagementContext().getLocationRegistry().updateDefinedLocation(definition);
        }

        log.info("New Docker host location {} created", location);
        return (CloudMachineLocation) location;
    }

    @Override
    public boolean isLocationAvailable() {
        return getDynamicLocation() != null;
    }

    @Override
    public JcloudsLocation getJcloudsLocation() {
        return jcloudsLocation;
    }

    @Override
    public void start(Collection<? extends Location> locations) {
        super.start(locations);

        Maybe<SshMachineLocation> found = Machines.findUniqueSshMachineLocation(getLocations());

        Map<String, ?> flags = MutableMap.<String, Object>builder()
                .putAll(getConfig(LOCATION_FLAGS))
                .put("machine", found.get())
                .build();

        createLocation(flags);
    }

    @Override
    public void stop() {
        deleteLocation();

        super.stop();
    }

    @Override
    public void deleteLocation() {
        CloudMachineLocation host = getDynamicLocation();

        if (host != null) {
            LocationManager mgr = getManagementContext().getLocationManager();
            if (mgr.isManaged(host)) {
                mgr.unmanage(host);
            }
            if (getConfig(CloudEnvironment.REGISTER_CLOUD_MACHINE_LOCATIONS)) {
                getManagementContext().getLocationRegistry().removeDefinedLocation(host.getId());
            }
        }
        setAttribute(DYNAMIC_LOCATION, null);
        setAttribute(LOCATION_NAME, null);
    }
}
