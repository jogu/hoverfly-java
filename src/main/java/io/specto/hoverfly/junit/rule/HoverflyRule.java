/**
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this classpath except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 * <p>
 * Copyright 2016-2016 SpectoLabs Ltd.
 */
package io.specto.hoverfly.junit.rule;

import io.specto.hoverfly.junit.core.Hoverfly;
import io.specto.hoverfly.junit.core.HoverflyConfig;
import io.specto.hoverfly.junit.core.HoverflyMode;
import io.specto.hoverfly.junit.core.SimulationResource;
import org.junit.rules.ExternalResource;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

import static io.specto.hoverfly.junit.core.HoverflyConfig.configs;
import static io.specto.hoverfly.junit.core.HoverflyMode.CAPTURE;
import static io.specto.hoverfly.junit.core.HoverflyMode.SIMULATE;
import static io.specto.hoverfly.junit.rule.HoverflyRuleUtils.fileRelativeToTestResources;
import static io.specto.hoverfly.junit.rule.HoverflyRuleUtils.isAnnotatedWithRule;


/**
 * <p>The Hoverfly Rule auto-spins up a Hoverfly process, and tears it down at the end of your tests.  It also configures the JVM
 * proxy to use Hoverfly, so so long as your client respects these proxy settings you shouldn't have to configure it.</p>
 * <p>
 * <p>Example of usage:
 * <pre>
 *     {@code
 * public class SomeTest {
 *           @ClassRule
 *           public HoverflyRule hoverflyRule = HoverflyRule.inSimulationMode(classpath("test-service.json"))
 *           @Test
 *           public void test() { //All requests will be proxied through Hoverfly
 *              // Given
 *              final RequestEntity<Void> bookFlightRequest = RequestEntity.delete(new URI("http://www.other-anotherService.com/api/bookings/1")).build();
 *
 *              // When
 *              final ResponseEntity<Void> bookFlightResponse = restTemplate.exchange(bookFlightRequest, Void.class);
 *
 *              // Then
 *              assertThat(bookFlightResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
 *           }
 *       }
 *     }
 * </pre>
 * <p>
 * <p>You can provide data from a Hoverfly JSON simulation, or alternatively you can use a DSL - {@link io.specto.hoverfly.junit.dsl.HoverflyDsl}</p>
 * <p>
 * <p>It is also possible to capture data:</p>
 * <p>
 * <pre>
 *     {@code
 *     @Rule
 *     public HoverflyRule hoverflyRule = HoverflyRule.inCaptureMode("recorded-simulation.json");
 *     }
 * </pre>
 * <p>
 * <p>The output will end up in your /src/test directory</p>
 * <p>
 * <p><b>It's recommended use always use the {@link org.junit.ClassRule} annotation, so we keep the same instance of Hoverfly through all your tests.</b>
 * This avoids the overhead of starting Hoverfly multiple times, and also helps ensure all your system properties are set before executing any other code.
 * If you want to change the data, you can do so in {@link org.junit.Before} method by calling {@link HoverflyRule#simulate}, but this will not be thread safe</p>
 *
 * @see SimulationResource
 * @see io.specto.hoverfly.junit.dsl.HoverflyDsl
 *
 * @since 4.7
 */
public class HoverflyRule extends ExternalResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(HoverflyRule.class);
    private final Hoverfly hoverfly;
    private final SimulationResource simulationResource;
    private final HoverflyMode hoverflyMode;
    private final Path capturePath;

    private HoverflyRule(final SimulationResource simulationResource, final HoverflyConfig hoverflyConfig) {
        this.hoverflyMode = SIMULATE;
        this.hoverfly = new Hoverfly(hoverflyConfig, hoverflyMode);
        this.simulationResource = simulationResource;
        this.capturePath = null;
    }

    private HoverflyRule(final Path capturePath, final HoverflyConfig hoverflyConfig) {
        this.hoverflyMode = CAPTURE;
        this.hoverfly = new Hoverfly(hoverflyConfig, hoverflyMode);
        this.simulationResource = null;
        this.capturePath = capturePath;
    }

    /**
     * Instantiates a rule which runs Hoverfly in capture mode
     *
     * @param recordedFilename the path to the recorded name relative to src/test/resources
     * @return HoverflyRule the rule instance
     */
    public static HoverflyRule inCaptureMode(String recordedFilename) {
        return inCaptureMode(recordedFilename, configs());
    }

    /**
     * Instantiates a rule which runs Hoverfly in capture mode
     *
     * @param recordedFilename the path to the recorded name relative to src/test/resources
     * @param hoverflyConfig   the config
     * @return the rule
     */
    public static HoverflyRule inCaptureMode(String recordedFilename, HoverflyConfig hoverflyConfig) {
        return new HoverflyRule(fileRelativeToTestResources(recordedFilename), hoverflyConfig);
    }

    /**
     * Instantiates a rule which runs Hoverfly in simulate mode
     *
     * @param simulationResource the simulation to import
     * @return the rule
     */
    public static HoverflyRule inSimulationMode(final SimulationResource simulationResource) {
        return inSimulationMode(simulationResource, configs());
    }


    public static HoverflyRule inSimulationMode(final SimulationResource simulationResource, HoverflyConfig hoverflyConfig) {
        return new HoverflyRule(simulationResource, hoverflyConfig);
    }


    /**
     * Instantiates a rule which runs Hoverfly in simulate mode with no data
     *
     * @return the rule
     */
    public static HoverflyRule inSimulationMode() {
        return inSimulationMode(configs());
    }

    /**
     * Instantiates a rule which runs Hoverfly in simulate mode with no data
     *
     * @param hoverflyConfig the config
     * @return the rule
     */
    public static HoverflyRule inSimulationMode(final HoverflyConfig hoverflyConfig) {
        return inSimulationMode(SimulationResource.empty(), hoverflyConfig);
    }

    @Override
    public Statement apply(Statement base, Description description) {
        if (isAnnotatedWithRule(description)) {
            LOGGER.warn(" It is recommended to use HoverflyRule with @ClassRule to get better performance in your tests, and prevent known issue with Apache HttpClient. For more information, please see https://github.com/SpectoLabs/hoverfly-junit.");
        }
        return super.apply(base, description);
    }

    /**
     * Starts in instance of Hoverfly
     * <p>
     * {@inheritDoc ExternalResource#before}
     *
     * @throws Throwable
     */
    @Override
    protected void before() throws Throwable {
        hoverfly.start();

        if (hoverflyMode == SIMULATE && simulationResource != null) {
            hoverfly.importSimulation(simulationResource);
        }
    }

    /**
     * Stops the managed instance of Hoverfly
     * <p>
     * {@inheritDoc ExternalResource#after}
     *
     * @throws Throwable
     */
    @Override
    protected void after() {
        try {
            if (hoverflyMode == CAPTURE) {
                hoverfly.exportSimulation(capturePath);
            }
        } finally {
            hoverfly.stop();
        }
    }

    /**
     * Gets the proxy port this has run on, which could be useful when running Hoverfly on a random port.
     *
     * @return the proxy port
     */
    public int getProxyPort() {
        return hoverfly.getProxyPort();
    }


    /**
     * Changes the Simulation used by Hoverfly
     * @param simulationResource the simulation
     */
    public void simulate(SimulationResource simulationResource) {
        hoverfly.importSimulation(simulationResource);
    }

}
