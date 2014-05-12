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
package brooklyn.location.cloud;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.cloud.CloudEnvironment;
import brooklyn.entity.cloud.CloudMachine;
import brooklyn.location.Location;
import brooklyn.location.MachineLocation;
import brooklyn.location.MachineProvisioningLocation;
import brooklyn.location.NoMachinesAvailableException;
import brooklyn.location.basic.AbstractLocation;
import brooklyn.location.basic.LocationConfigKeys;
import brooklyn.location.basic.Machines;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.location.dynamic.DynamicLocation;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.guava.Maybe;

import com.google.common.base.Objects.ToStringHelper;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.common.reflect.TypeToken;

public class CloudLocation extends AbstractLocation implements MachineProvisioningLocation<MachineLocation>,
        DynamicLocation<CloudEnvironment, CloudLocation> {

	private static final Logger LOG = LoggerFactory.getLogger(CloudLocation.class);

	public static final ConfigKey<MachineProvisioningLocation<SshMachineLocation>> PROVISIONER =
            ConfigKeys.newConfigKey(new TypeToken<MachineProvisioningLocation<SshMachineLocation>>() { },
                    "cloud.machine.provisioner", "The underlying provisioner for VMs");

	public static final String PREFIX = "cloud-";

    @SetFromFlag("mutex")
    private Object mutex;

    @SetFromFlag("owner")
    private CloudEnvironment infrastructure;

    @SetFromFlag("provisioner")
    private MachineProvisioningLocation<SshMachineLocation> provisioner;

    /* Mappings for provisioned locations */

    private final Set<SshMachineLocation> obtained = Sets.newHashSet();
    private final Multimap<SshMachineLocation, String> machines = HashMultimap.create();
    private final Map<String, SshMachineLocation> containers = Maps.newHashMap();

    public CloudLocation() {
        this(Maps.newLinkedHashMap());
    }

    public CloudLocation(Map properties) {
        super(properties);

        if (isLegacyConstruction()) {
            init();
        }
    }

    public MachineProvisioningLocation<SshMachineLocation> getProvisioner() {
        return provisioner;
    }

    @Override
    public void configure(Map properties) {
        if (mutex == null) {
            mutex = new Object[0];
        }
        super.configure(properties);
    }

    public MachineLocation obtain() throws NoMachinesAvailableException {
        return obtain(Maps.<String,Object>newLinkedHashMap());
    }

    @Override
    public MachineLocation obtain(Map<?,?> flags) throws NoMachinesAvailableException {
        synchronized (mutex) {
            // Check context for entity being deployed
            Object context = flags.get(LocationConfigKeys.CALLER_CONTEXT.getName());
            if (!(context instanceof Entity)) {
                throw new IllegalStateException("Invalid location context: " + context);
            }
            Entity entity = (Entity) context;

            // Look for idle CloudMachine first
            getOwner().getCloudMachineList()

            // Obtain a new machine location
            MachineLocation machine = provisioner.obtain(flags);

            Maybe<SshMachineLocation> deployed = Machines.findUniqueSshMachineLocation(dockerHost.getLocations());
            if (deployed.isPresent()) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Storing container mapping {} to {}", deployed.get(), machine.getId());
                }
                machines.put(machine, dockerHost.getId());
                containers.put(container.getId(), deployed.get());
            }
            return machine;
        }
    }

    @Override
    public MachineProvisioningLocation<MachineLocation> newSubLocation(Map<?, ?> newFlags) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void release(MachineLocation machine) {
        if (provisioner != null) {
            synchronized (mutex) {
                String id = machine.getId();
                SshMachineLocation ssh = containers.remove(id);
                if (ssh != null) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Request to remove container mapping {} to {}", ssh, id);
                    }
                    if (machines.remove(ssh, id)) {
                        if (machines.get(ssh).isEmpty()) {
                            if (LOG.isDebugEnabled()) {
                                LOG.debug("Empty Docker host at {}", ssh);
                            }
                        }
                    } else {
                        throw new IllegalArgumentException("Request to release "+machine+", but container mapping not found");
                    }
                } else {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Request to release machine {}", machine);
                    }
                    if (obtained.remove(machine)) {
                        provisioner.release((SshMachineLocation) machine);
                    } else {
                        throw new IllegalArgumentException("Request to release "+machine+", but this machine is not currently allocated");
                    }
                }
            }
        } else {
            throw new IllegalStateException("No provisioner available to release "+machine);
        }
    }

    @Override
    public Map<String,Object> getProvisioningFlags(Collection<String> tags) {
        return Maps.newLinkedHashMap();
    }

    public List<Entity> getDockerContainerList() {
        return infrastructure.getDockerContainerList();
    }

    public List<Entity> getDockerHostList() {
        return infrastructure.getDockerHostList();
    }

    public CloudEnvironment getDockerInfrastructure() {
        return infrastructure;
    }

    @Override
    public ToStringHelper string() {
        return super.string()
                .omitNullValues()
                .add("provisioner", provisioner)
                .add("infrastructure", infrastructure)
                .add("strategy", strategy);
    }

    @Override
    public CloudEnvironment getOwner() {
        return infrastructure;
    }

}
