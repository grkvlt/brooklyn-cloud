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

import static java.lang.String.format;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.Callable;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.BasicStartableImpl;
import brooklyn.entity.software.SshEffectorTasks;
import brooklyn.event.feed.function.FunctionFeed;
import brooklyn.event.feed.function.FunctionPollConfig;
import brooklyn.location.Location;
import brooklyn.location.LocationDefinition;
import brooklyn.location.basic.BasicLocationDefinition;
import brooklyn.location.basic.Machines;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.location.cloud.CloudLocation;
import brooklyn.location.cloud.CloudMachineLocation;
import brooklyn.location.cloud.CloudResolver;
import brooklyn.location.jclouds.JcloudsLocation;
import brooklyn.management.LocationManager;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.guava.Maybe;
import brooklyn.util.task.DynamicTasks;
import brooklyn.util.task.system.ProcessTaskWrapper;
import brooklyn.util.time.Duration;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.collect.ImmutableList;

public class CloudMachineImpl extends BasicStartableImpl implements CloudMachine {

    private static final Logger log = LoggerFactory.getLogger(CloudMachineImpl.class);

    private transient JcloudsLocation jcloudsLocation;
    private transient FunctionFeed sensorFeed;

    @Override
    public void init() {
        log.info("Starting machine id {}", getId());
    }

    protected void connectSensors() {
        sensorFeed = FunctionFeed.builder()
                .entity(this)
                .period(Duration.TEN_SECONDS)
                .poll(new FunctionPollConfig<Boolean, Boolean>(CloudMachine.SSH_AVAILABLE)
                        .callable(new Callable<Boolean>() {
                                @Override
                                public Boolean call() throws Exception {
                                    return getSshMachine().isSshable();
                                }
                        }))
                .poll(new FunctionPollConfig<String, Double>(CloudMachine.CPU_USAGE)
                        .callable(new Callable<String>() {
                                @Override
                                public String call() throws Exception {
                                    ProcessTaskWrapper<Integer> task = SshEffectorTasks.ssh(ImmutableList.of("uptime"))
                                            .machine(getSshMachine())
                                            .requiringExitCodeZero()
                                            .summary("cpuUsage")
                                            .newTask();
                                    DynamicTasks.queueIfPossible(task).orSubmitAsync(CloudMachineImpl.this);
                                    return task.block().getStdout();
                                }
                            })
                        .onFailureOrException(Functions.constant(0d))
                        .onSuccess(new Function<String, Double>() {
                                @Override
                                public Double apply(@Nullable String input) {
                                    log.info("Uptime: {}", input);
                                    // TODO parse uptime output
                                    return 1d;
                                }
                            }))
                .build();
    }

    protected void disconnectSensors() {
        if (sensorFeed != null) sensorFeed.stop();
    }

    @Override
    public String getShortName() {
        return "Machine";
    }

    @Override
    public CloudEnvironment getEnvironment() {
        return getConfig(CLOUD_ENVIRONMENT);
    }

    @Override
    public CloudMachineLocation getDynamicLocation() {
        return (CloudMachineLocation) getAttribute(DYNAMIC_LOCATION);
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
    public Entity getRunningEntity() {
        return getAttribute(ENTITY);
    }

    @Override
    public void setRunningEntity(Entity entity) {
        setAttribute(ENTITY, entity);
    }

    @Override
    public SshMachineLocation getSshMachine() {
        return getAttribute(SSH_MACHINE);
    }

    @Override
    public boolean isSshable() {
        return getAttribute(SSH_AVAILABLE);
    }

    /**
     * Create a new {@link CloudMachineLocation} wrapping the machine we are starting in.
     */
    @Override
    public CloudMachineLocation createLocation(Map<String, ?> flags) {
        String locationSpec, locationName;
        CloudEnvironment infrastructure = getConfig(CLOUD_ENVIRONMENT);
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

        log.info("New cloud machine location {} created", location);
        return (CloudMachineLocation) location;
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

    @Override
    public void start(Collection<? extends Location> locations) {
        setAttribute(SERVICE_UP, Boolean.FALSE);

        Maybe<SshMachineLocation> found = Machines.findUniqueSshMachineLocation(locations);

        Map<String, ?> flags = MutableMap.<String, Object>builder()
                .putAll(getConfig(LOCATION_FLAGS))
                .put("machine", found.get())
                .build();
        createLocation(flags);

        setAttribute(SSH_MACHINE, getDynamicLocation().getMachine());

        connectSensors();

        super.start(locations);

        setAttribute(SERVICE_UP, Boolean.TRUE);
    }

    @Override
    public void stop() {
        setAttribute(SERVICE_UP, Boolean.FALSE);

        super.stop();

        disconnectSensors();

        setAttribute(SSH_MACHINE, null);

        deleteLocation();
    }

}
