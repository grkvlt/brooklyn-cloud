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

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.entity.trait.HasShortName;
import brooklyn.location.cloud.CloudMachineLocation;
import brooklyn.location.dynamic.LocationOwner;
import brooklyn.location.jclouds.JcloudsLocation;
import brooklyn.util.flags.SetFromFlag;

@ImplementedBy(CloudMachineImpl.class)
public interface CloudMachine extends Entity, HasShortName, LocationOwner<CloudMachineLocation, CloudMachine> {

    @SetFromFlag("infrastructure")
    ConfigKey<CloudEnvironment> DOCKER_INFRASTRUCTURE = ConfigKeys.newConfigKey(CloudEnvironment.class,
            "docker.infrastructure", "The parent Docker infrastructure");

    JcloudsLocation getJcloudsLocation();

    CloudEnvironment getInfrastructure();

    Entity getRunningEntity();
    void setRunningEntity(Entity entity);

}
