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

import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.enricher.Enrichers;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.BasicStartableImpl;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.basic.SoftwareProcess.ChildStartableMode;
import brooklyn.entity.group.Cluster;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.group.DynamicMultiGroup;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.Location;
import brooklyn.location.LocationDefinition;
import brooklyn.location.LocationSpec;
import brooklyn.location.MachineProvisioningLocation;
import brooklyn.location.basic.BasicLocationDefinition;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.location.cloud.CloudLocation;
import brooklyn.location.cloud.CloudMachineLocation;
import brooklyn.location.cloud.CloudResolver;
import brooklyn.management.LocationManager;
import brooklyn.util.collections.MutableMap;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public class CloudEnvironmentImpl extends BasicStartableImpl implements CloudEnvironment {

    private static final Logger log = LoggerFactory.getLogger(CloudEnvironmentImpl.class);

    private DynamicCluster machines;
    private DynamicMultiGroup buckets;

    private Predicate<Entity> sameInfrastructure = new Predicate<Entity>() {
        @Override
        public boolean apply(@Nullable Entity input) {
            Optional<Location> lookup = Iterables.tryFind(input.getLocations(), Predicates.instanceOf(CloudMachineLocation.class));
            if (lookup.isPresent()) {
                CloudMachineLocation machine = (CloudMachineLocation) lookup.get();
                return getId().equals(machine.getOwner().getEnvironment().getId());
            } else {
                return false;
            }
        }
    };

    @Override
    public void init() {
        int initialSize = getConfig(CLOUD_MACHINE_CLUSTER_MIN_SIZE);
        EntitySpec<?> dockerHostSpec = EntitySpec.create(getConfig(CLOUD_MACHINE_SPEC))
                .configure(CloudMachine.CLOUD_ENVIRONMENT, this)
                .configure(SoftwareProcess.CHILDREN_STARTABLE_MODE, ChildStartableMode.BACKGROUND_LATE);

        machines = addChild(EntitySpec.create(DynamicCluster.class)
                .configure(Cluster.INITIAL_SIZE, initialSize)
                .configure(DynamicCluster.QUARANTINE_FAILED_ENTITIES, true)
                .configure(DynamicCluster.MEMBER_SPEC, dockerHostSpec)
                .displayName("Machines"));

        buckets = addChild(EntitySpec.create(DynamicMultiGroup.class)
                .configure(DynamicMultiGroup.ENTITY_FILTER, sameInfrastructure)
                .configure(DynamicMultiGroup.RESCAN_INTERVAL, 15L)
                .configure(DynamicMultiGroup.BUCKET_FUNCTION, new Function<Entity, String>() {
                        @Override
                        public String apply(@Nullable Entity input) {
                            return input.getApplication().getDisplayName();
                        }
                    })
                .displayName("Applications"));

        if (Entities.isManaged(this)) {
            Entities.manage(machines);
            Entities.manage(buckets);
        }

        machines.addEnricher(Enrichers.builder()
                .aggregating(CloudAttributes.AVERAGE_CPU_USAGE)
                .computingAverage()
                .fromMembers()
                .publishing(CloudAttributes.AVERAGE_CPU_USAGE)
                .build());
    }

    @Override
    public Iterable<Entity> getAvailableMachines() {
        return Iterables.filter(getCloudMachineList(), new Predicate<Entity>() {
            @Override
            public boolean apply(@Nullable Entity input) {
                return input.getAttribute(CloudAttributes.ENTITY) == null;
            }
        });
    }

    @Override
    public List<Entity> getCloudMachineList() {
        if (machines == null) {
            return ImmutableList.of();
        } else {
            return ImmutableList.copyOf(machines.getMembers());
        }
    }

    @Override
    public DynamicCluster getCloudMachineCluster() { return machines; }

    @Override
    public Integer resize(Integer desiredSize) {
        return machines.resize(desiredSize);
    }

    @Override
    public Integer getCurrentSize() {
        return machines.getCurrentSize();
    }

    @Override
    public CloudLocation getDynamicLocation() {
        return (CloudLocation) getAttribute(DYNAMIC_LOCATION);
    }

    @Override
    public boolean isLocationAvailable() {
        return getDynamicLocation() != null;
    }

    @Override
    public CloudLocation createLocation(Map<String, ?> flags) {
        String locationName = getConfig(LOCATION_NAME);
        if (locationName == null) {
            String prefix = getConfig(LOCATION_NAME_PREFIX);
            String suffix = getConfig(LOCATION_NAME_SUFFIX);
            locationName = Joiner.on("-").skipNulls().join(prefix, getId(), suffix);
        }
        String locationSpec = String.format(CloudResolver.CLOUD_ENVIRONMENT_SPEC, getId()) +
                String.format(":(name=\"%s\")", locationName);
        setAttribute(LOCATION_SPEC, locationSpec);
        LocationDefinition definition = new BasicLocationDefinition(locationName, locationSpec, flags);
        Location location = getManagementContext().getLocationRegistry().resolve(definition);

        setAttribute(DYNAMIC_LOCATION, location);
        setAttribute(LOCATION_NAME, location.getId());
        getManagementContext().getLocationRegistry().updateDefinedLocation(definition);

        log.info("New cloud location {} created", location);
        return (CloudLocation) location;
    }

    @Override
    public void deleteLocation() {
        CloudLocation location = getDynamicLocation();

        if (location != null) {
            LocationManager mgr = getManagementContext().getLocationManager();
            if (mgr.isManaged(location)) {
                mgr.unmanage(location);
            }
        }

        setAttribute(DYNAMIC_LOCATION, null);
        setAttribute(LOCATION_NAME, null);
    }

    @Override
    public void start(Collection<? extends Location> locations) {
        Optional<? extends Location> location = Optional.absent();
        if (Iterables.size(locations) > 0) {
            location = Iterables.tryFind(locations, Predicates.instanceOf(MachineProvisioningLocation.class));
        }
        Location provisioner;
        if (!location.isPresent() || location.get() instanceof LocalhostMachineProvisioningLocation) {
            LocationSpec<?> spec = getConfig(CLOUD_LOCATION_SPEC);
            provisioner = getManagementContext().getLocationManager().createLocation(spec);
        } else {
            provisioner = location.get();
        }
        log.info("Creating new CloudLocation wrapping {}", provisioner);

        Map<String, ?> flags = MutableMap.<String, Object>builder()
                .putAll(getConfig(LOCATION_FLAGS))
                .put("provisioner", provisioner)
                .build();
        createLocation(flags);

        super.start(locations);
    }

    /**
     * De-register our {@link CloudLocation} and its children.
     */
    public void stop() {
        super.stop();

        deleteLocation();
    }

}
