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

import java.util.List;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.BasicStartable;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.entity.trait.Resizable;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.location.LocationSpec;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.location.cloud.CloudLocation;
import brooklyn.location.dynamic.LocationOwner;
import brooklyn.location.jclouds.JcloudsLocationConfig;
import brooklyn.util.flags.SetFromFlag;

@ImplementedBy(CloudEnvironmentImpl.class)
public interface CloudEnvironment extends BasicStartable, Resizable, LocationOwner<CloudLocation, CloudEnvironment> {

    @SetFromFlag("securityGroup")
    ConfigKey<String> SECURITY_GROUP = ConfigKeys.newStringConfigKey(
            "cloud.machine.securityGroup", "Set a network security group for cloud servers to use; (null to use default configuration)");

    @SetFromFlag("openIptables")
    ConfigKey<Boolean> OPEN_IPTABLES = ConfigKeys.newConfigKeyWithPrefix("cloud.machine.", JcloudsLocationConfig.OPEN_IPTABLES);

    @SetFromFlag("initialSize")
    ConfigKey<Integer> CLOUD_MACHINE_CLUSTER_MIN_SIZE = ConfigKeys.newConfigKeyWithPrefix("cloud.machine.", DynamicCluster.INITIAL_SIZE);

    @SetFromFlag("registerMachines")
    ConfigKey<Boolean> REGISTER_CLOUD_MACHINE_LOCATIONS = ConfigKeys.newBooleanConfigKey("cloud.machine.register",
            "Register new cloud machine locations for deployment", Boolean.FALSE);

    @SetFromFlag("cloudSpec")
    BasicAttributeSensorAndConfigKey<LocationSpec> CLOUD_LOCATION_SPEC = new BasicAttributeSensorAndConfigKey<LocationSpec>(
            LocationSpec.class, "cloud.location.spec", "Specification to use for the cloud environment",
            LocationSpec.create(LocalhostMachineProvisioningLocation.class));

    @SetFromFlag("machineSpec")
    BasicAttributeSensorAndConfigKey<EntitySpec> CLOUD_MACHINE_SPEC = new BasicAttributeSensorAndConfigKey<EntitySpec>(
            EntitySpec.class, "cloud.machine.spec", "Specification to use when creating cloud machines",
            EntitySpec.create(CloudMachine.class));

    AttributeSensor<Integer> CLOUD_MACHINE_COUNT = CloudAttributes.CLOUD_MACHINE_COUNT;
    AttributeSensor<Integer> CLOUD_MACHINE_IDLE_COUNT = CloudAttributes.CLOUD_MACHINE_IDLE_COUNT;

    List<Entity> getCloudMachineList();

    DynamicCluster getCloudMachineCluster();

    Iterable<Entity> getAvailableMachines();

}
