/* Copyright 2017-2018 the ros2-java contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.ros2.rcljava.parameters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.ref.WeakReference;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import java.util.concurrent.Future;

import org.ros2.rcljava.RCLJava;
import org.ros2.rcljava.concurrent.RCLFuture;
import org.ros2.rcljava.consumers.Consumer;
import org.ros2.rcljava.node.Node;
import org.ros2.rcljava.parameters.ParameterVariant;
import org.ros2.rcljava.parameters.client.AsyncParametersClient;
import org.ros2.rcljava.parameters.client.AsyncParametersClientImpl;
import org.ros2.rcljava.parameters.service.ParameterService;
import org.ros2.rcljava.parameters.service.ParameterServiceImpl;

public class AsyncParametersClientTest {
  private static class TestConsumer<T> implements Consumer<Future<T>> {
    private RCLFuture<T> resultFuture;

    public void accept(final Future<T> future) {
      T result = null;
      try {
        result = future.get();
      } catch (Exception e) {
        // TODO(esteve): do something
      }
      this.resultFuture.set(result);
    }

    public TestConsumer(RCLFuture<T> resultFuture) {
      this.resultFuture = resultFuture;
    }
  }

  private Node node;
  private ParameterService parameterService;
  private AsyncParametersClient parametersClient;

  @BeforeClass
  public static void setupOnce() throws Exception {
    RCLJava.rclJavaInit();
    org.apache.log4j.BasicConfigurator.configure();
  }

  @Before
  public void setUp() throws Exception {
    node = RCLJava.createNode("test_node");
    parameterService = new ParameterServiceImpl(node);
    parametersClient = new AsyncParametersClientImpl(node);
  }

  @After
  public void tearDown() {
    node.dispose();
  }

  @AfterClass
  public static void tearDownOnce() {
    RCLJava.shutdown();
  }

  @Test
  public final void testSetParameters() throws Exception {
    List<ParameterVariant> parameters = Arrays.asList(new ParameterVariant[] {
        new ParameterVariant("foo", 2), new ParameterVariant("bar", "hello"),
        new ParameterVariant("baz", 1.45), new ParameterVariant("foo.first", 8),
        new ParameterVariant("foo.second", 42), new ParameterVariant("foobar", true)});

    RCLFuture<List<rcl_interfaces.msg.SetParametersResult>> future =
        new RCLFuture<List<rcl_interfaces.msg.SetParametersResult>>(
            new WeakReference<Node>(this.node));
    parametersClient.setParameters(parameters, new TestConsumer(future));

    List<String> parameterNames =
        Arrays.asList(new String[] {"foo", "bar", "baz", "foo.first", "foo.second", "foobar"});

    List<ParameterVariant> results = node.getParameters(parameterNames);
    assertEquals(parameters, future.get());
  }

  @Test
  public final void testGetParameters() throws Exception {
    List<ParameterVariant> parameters = Arrays.asList(new ParameterVariant[] {
        new ParameterVariant("foo", 2), new ParameterVariant("bar", "hello"),
        new ParameterVariant("baz", 1.45), new ParameterVariant("foo.first", 8),
        new ParameterVariant("foo.second", 42), new ParameterVariant("foobar", true)});

    node.setParameters(parameters);

    List<String> parameterNames =
        Arrays.asList(new String[] {"foo", "bar", "baz", "foo.first", "foo.second", "foobar"});

    RCLFuture<List<ParameterVariant>> future =
        new RCLFuture<List<ParameterVariant>>(new WeakReference<Node>(this.node));
    parametersClient.getParameters(parameterNames, new TestConsumer(future));

    assertEquals(parameters, future.get());
  }

  @Test
  public final void testListParameters() throws Exception {
    List<ParameterVariant> parameters = Arrays.asList(new ParameterVariant[] {
        new ParameterVariant("foo", 2), new ParameterVariant("bar", "hello"),
        new ParameterVariant("baz", 1.45), new ParameterVariant("foo.first", 8),
        new ParameterVariant("foo.second", 42), new ParameterVariant("foobar", true)});

    node.setParameters(parameters);

    RCLFuture<rcl_interfaces.msg.ListParametersResult> future =
        new RCLFuture<rcl_interfaces.msg.ListParametersResult>(new WeakReference<Node>(this.node));
    parametersClient.listParameters(
        Arrays.asList(new String[] {"foo", "bar"}), 10, new TestConsumer(future));

    assertEquals(Arrays.asList(new String[] {"foo.first", "foo.second"}), future.get().getNames());
    assertEquals(Arrays.asList(new String[] {"foo"}), future.get().getPrefixes());
  }

  @Test
  public final void testDescribeParameters() throws Exception {
    List<ParameterVariant> parameters = Arrays.asList(new ParameterVariant[] {
        new ParameterVariant("foo", 2), new ParameterVariant("bar", "hello"),
        new ParameterVariant("baz", 1.45), new ParameterVariant("foo.first", 8),
        new ParameterVariant("foo.second", 42), new ParameterVariant("foobar", true)});

    node.setParameters(parameters);

    RCLFuture<List<rcl_interfaces.msg.ParameterDescriptor>> future =
        new RCLFuture<List<rcl_interfaces.msg.ParameterDescriptor>>(
            new WeakReference<Node>(this.node));
    parametersClient.describeParameters(
        Arrays.asList(new String[] {"foo", "bar"}), new TestConsumer(future));

    List<rcl_interfaces.msg.ParameterDescriptor> expected =
        Arrays.asList(new rcl_interfaces.msg.ParameterDescriptor[] {
            new rcl_interfaces.msg.ParameterDescriptor().setName("foo").setType(
                rcl_interfaces.msg.ParameterType.PARAMETER_INTEGER),
            new rcl_interfaces.msg.ParameterDescriptor().setName("bar").setType(
                rcl_interfaces.msg.ParameterType.PARAMETER_STRING)});
    assertEquals(expected, future.get());
  }
}
