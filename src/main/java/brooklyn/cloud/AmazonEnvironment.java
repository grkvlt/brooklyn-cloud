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
package brooklyn.cloud;

import java.util.Map;

import brooklyn.catalog.Catalog;
import brooklyn.catalog.CatalogConfig;
import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.cloud.CloudEnvironment;
import brooklyn.entity.cloud.CloudMachine;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.LocationSpec;
import brooklyn.location.jclouds.JcloudsLocation;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.text.Strings;

/**
 * Brooklyn managed AWS cloud environment.
 */
@Catalog(name="Amazon Environemnt",
        description="Managed AWS Cloud Environemnt.",
        iconUrl="classpath://aws-logo.png")
public class AmazonEnvironment extends AbstractApplication {

    @CatalogConfig(label="Region", priority=0)
    public static final ConfigKey<String> REGION = ConfigKeys.newStringConfigKey("cloud.region", "The AWS region name", "eu-west-1");

    @CatalogConfig(label="Hardware", priority=0)
    public static final ConfigKey<String> HARDWARE = ConfigKeys.newStringConfigKey("cloud.hardware", "The AWS hardware type", "m1.medium");

    @CatalogConfig(label="Identity (Optional)", priority=0)
    public static final ConfigKey<String> IDENTITY = ConfigKeys.newStringConfigKey("cloud.identity", "The IAS identity");

    @CatalogConfig(label="Credential (Optional)", priority=0)
    public static final ConfigKey<String> CREDENTIAL = ConfigKeys.newStringConfigKey("cloud.credential", "The IAS credential");

    @CatalogConfig(label="Location Name", priority=1)
    public static final ConfigKey<String> LOCATION_NAME = ConfigKeys.newConfigKeyWithDefault(CloudEnvironment.LOCATION_NAME.getConfigKey(), "amazon-environment");

    @CatalogConfig(label="Cloud Cluster Minimum Size", priority=1)
    public static final ConfigKey<Integer> CLOUD_MACHINE_CLUSTER_MIN_SIZE = ConfigKeys.newConfigKeyWithDefault(CloudEnvironment.CLOUD_MACHINE_CLUSTER_MIN_SIZE, 1);

    @CatalogConfig(label="Cloud Flags", priority=1)
    public static final ConfigKey<Map<String, Object>> LOCATION_FLAGS = ConfigKeys.newConfigKeyWithDefault(CloudEnvironment.LOCATION_FLAGS, MutableMap.<String, Object>of());

    @Override
    public void init() {
        LocationSpec<?> awsSpec = LocationSpec.create(JcloudsLocation.class)
                .configure(JcloudsLocation.CLOUD_PROVIDER, "aws-ec2:" + getConfig(REGION))
                .configure(JcloudsLocation.HARDWARE_ID, getConfig(HARDWARE));
        if (Strings.isNonEmpty(getConfig(IDENTITY)) && Strings.isNonEmpty(getConfig(CREDENTIAL))) {
            awsSpec.configure(JcloudsLocation.ACCESS_IDENTITY, getConfig(IDENTITY))
                    .configure(JcloudsLocation.ACCESS_CREDENTIAL, getConfig(CREDENTIAL));
        }

        EntitySpec<?> vmSpec = EntitySpec.create(CloudMachine.class)
                .configure(CloudMachine.LOCATION_FLAGS, getConfig(LOCATION_FLAGS));

        addChild(EntitySpec.create(CloudEnvironment.class)
                .configure(CloudEnvironment.SECURITY_GROUP, "universal") // AWS EC2 All TCP and UDP ports from 0.0.0.0/0
                .configure(CloudEnvironment.OPEN_IPTABLES, true)
                .configure(CloudEnvironment.LOCATION_NAME, getConfig(LOCATION_NAME))
                .configure(CloudEnvironment.CLOUD_LOCATION_SPEC, awsSpec)
                .configure(CloudEnvironment.CLOUD_MACHINE_CLUSTER_MIN_SIZE, getConfig(CLOUD_MACHINE_CLUSTER_MIN_SIZE))
                .configure(CloudEnvironment.CLOUD_MACHINE_SPEC, vmSpec)
                .displayName("Amazon Environment"));
    }

}
