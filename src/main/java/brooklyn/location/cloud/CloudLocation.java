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
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.cloud.CloudEnvironment;
import brooklyn.entity.cloud.CloudMachine;
import brooklyn.entity.cloud.MachineEntity;
import brooklyn.location.MachineLocation;
import brooklyn.location.MachineProvisioningLocation;
import brooklyn.location.NoMachinesAvailableException;
import brooklyn.location.basic.AbstractLocation;
import brooklyn.location.basic.LocationConfigKeys;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.location.dynamic.DynamicLocation;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.base.Objects.ToStringHelper;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Ordering;
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
    private CloudEnvironment environment;

    @SetFromFlag("provisioner")
    private MachineProvisioningLocation<SshMachineLocation> provisioner;

    /* Mappings for provisioned locations */

    private final Set<MachineLocation> obtained = Sets.newHashSet();

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
            if (context != null && !(context instanceof Entity)) {
                throw new IllegalStateException("Invalid location context: " + context);
            }
            Entity entity = (Entity) context;

            // Look for idle CloudMachine first
            Set<Entity> idle = Sets.newTreeSet(new Ordering<Entity>() {
                @Override
                public int compare(@Nullable Entity left, @Nullable Entity right) {
                    return ComparisonChain.start()
                            .compare(left.getAttribute(MachineEntity.CPU_USAGE), right.getAttribute(MachineEntity.CPU_USAGE))
                            .result();
                }
            });
            for (Entity e : getOwner().getCloudMachineList()) {
                if (Boolean.TRUE.equals(e.getAttribute(CloudMachine.SERVICE_UP)) && e.getAttribute(CloudMachine.ENTITY) == null) {
                    idle.add(e);
                }
            }
            if (idle.size() > 0) {
                CloudMachineLocation machine = (CloudMachineLocation) Iterables.getLast(idle);
                machine.setEntity(entity);
                return machine;
            }

            // Obtain a new machine location
            MachineLocation machine = provisioner.obtain(flags);
            obtained.add(machine);

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
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Request to release machine {}", machine);
                }
                if (obtained.remove(machine)) {
                    provisioner.release((SshMachineLocation) machine);
                } else {
                    throw new IllegalArgumentException("Request to release "+machine+", but this machine is not currently allocated");
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

    @Override
    public ToStringHelper string() {
        return super.string()
                .omitNullValues()
                .add("provisioner", provisioner)
                .add("environment", environment);
    }

    @Override
    public CloudEnvironment getOwner() {
        return environment;
    }

}
