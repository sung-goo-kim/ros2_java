/* Copyright 2016-2018 Esteve Fernandez <esteve@apache.org>
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

package org.ros2.rcljava;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.LinkedBlockingQueue;

import org.ros2.rcljava.client.Client;
import org.ros2.rcljava.common.JNIUtils;
import org.ros2.rcljava.contexts.Context;
import org.ros2.rcljava.contexts.ContextImpl;
import org.ros2.rcljava.executors.SingleThreadedExecutor;
import org.ros2.rcljava.interfaces.MessageDefinition;
import org.ros2.rcljava.interfaces.ServiceDefinition;
import org.ros2.rcljava.node.ComposableNode;
import org.ros2.rcljava.node.Node;
import org.ros2.rcljava.node.NodeImpl;
import org.ros2.rcljava.publisher.Publisher;
import org.ros2.rcljava.qos.QoSProfile;
import org.ros2.rcljava.service.RMWRequestId;
import org.ros2.rcljava.service.Service;
import org.ros2.rcljava.subscription.Subscription;
import org.ros2.rcljava.timer.Timer;

/**
 * Entry point for the ROS2 Java API, similar to the rclcpp API.
 */
public final class RCLJava {
  private static final Logger logger = LoggerFactory.getLogger(RCLJava.class);

  private static SingleThreadedExecutor globalExecutor = null;

  private static SingleThreadedExecutor getGlobalExecutor() {
    synchronized (RCLJava.class) {
      if (globalExecutor == null) {
        globalExecutor = new SingleThreadedExecutor();
      }
      return globalExecutor;
    }
  }

  /**
   * Private constructor so this cannot be instantiated.
   */
  private RCLJava() {}

  /**
   * The default context.
   */
  private static Context defaultContext;

  /**
   * All the @{link Context}s that have been created.
   * Does not include the default context.
   */
  private static Collection<Context> contexts;

  /**
   * All the @{link Node}s that have been created.
   */
  private static Collection<Node> nodes;

  private static void cleanup() {
    for (Node node : nodes) {
      for (Subscription subscription : node.getSubscriptions()) {
        subscription.dispose();
      }

      for (Publisher publisher : node.getPublishers()) {
        publisher.dispose();
      }

      for (Timer timer : node.getTimers()) {
        timer.dispose();
      }

      for (Service service : node.getServices()) {
        service.dispose();
      }

      for (Client client : node.getClients()) {
        client.dispose();
      }

      node.dispose();
    }
    nodes.clear();

    for (Context context : contexts) {
      context.dispose();
    }
    contexts.clear();
  }

  static {
    try {
      JNIUtils.loadImplementation(RCLJava.class);
    } catch (UnsatisfiedLinkError ule) {
      logger.error("Native code library failed to load.\n" + ule);
      System.exit(1);
    }

    nodes = new LinkedBlockingQueue<Node>();
    contexts = new LinkedBlockingQueue<Context>();

    // NOTE(esteve): disabling shutdown hook for now to avoid JVM crashes
    // Runtime.getRuntime().addShutdownHook(new Thread() {
    //   public void run() {
    //     cleanup();
    //   }
    // });
  }

  /**
   * Get the default context.
   */
  public static synchronized Context getDefaultContext() {
    if (RCLJava.defaultContext == null) {
      long contextHandle = nativeCreateContextHandle();
      RCLJava.defaultContext = new ContextImpl(contextHandle);
    }
    return RCLJava.defaultContext;
  }

  /**
   * @return true if RCLJava has been fully initialized, false otherwise.
   */
  @Deprecated
  public static boolean isInitialized() {
    return RCLJava.ok();
  }

  /**
   * Initialize the RCLJava API. If successful, a valid RMW implementation will
   *   be loaded and accessible, enabling the creating of ROS2 entities
   *   (@{link Node}s, @{link Publisher}s and @{link Subscription}s.
   *   This also initializes the default context.
   */
  public static synchronized void rclJavaInit() {
    if (RCLJava.ok()) {
      return;
    }

    // Initialize default context
    getDefaultContext().init();

    logger.info("Using RMW implementation: {}", RCLJava.getRMWIdentifier());
  }

  /**
   * Create a context (rcl_context_t) and return a pointer to it as an integer.
   *
   * @return A pointer to the underlying context structure.
   */
  private static native long nativeCreateContextHandle();

  /**
   * Create a ROS2 node (rcl_node_t) and return a pointer to it as an integer.
   *
   * @param nodeName The name that will identify this node in a ROS2 graph.
   * @param namespace The namespace of the node.
   * @param contextHandle Pointer to a context (rcl_context_t) with which to associated the node.
   * @return A pointer to the underlying ROS2 node structure.
   */
  private static native long nativeCreateNodeHandle(String nodeName, String namespace, long contextHandle);

  /**
   * @return The identifier of the currently active RMW implementation via the
   *     native ROS2 API.
   */
  private static native String nativeGetRMWIdentifier();

  /**
   * @return The identifier of the currently active RMW implementation.
   */
  public static String getRMWIdentifier() {
    return nativeGetRMWIdentifier();
  }

  /**
   * Call the underlying ROS2 rcl mechanism to check if ROS2 has been shut
   *   down.
   *
   * @return true if RCLJava hasn't been shut down, false otherwise.
   */
  private static native boolean nativeOk();

  /**
   * @return true if RCLJava hasn't been shut down, false otherwise.
   */
  public static boolean ok() {
    return RCLJava.getDefaultContext().isValid();
  }

  /**
   * @return true if RCLJava hasn't been shut down, false otherwise.
   */
  public static boolean ok(final Context context) {
    return context.isValid();
  }

  /**
   * Create a @{link Context}.
   *
   * @return A @{link Context} that represents the underlying context structure.
   */
  public static Context createContext() {
    long contextHandle = nativeCreateContextHandle();
    Context context = new ContextImpl(contextHandle);
    contexts.add(context);
    return context;
  }

  /**
   * Create a @{link Node}.
   *
   * @param nodeName The name that will identify this node in a ROS2 graph.
   * @return A @{link Node} that represents the underlying ROS2 node
   *     structure.
   */
  public static Node createNode(final String nodeName) {
    return createNode(nodeName, "", RCLJava.getDefaultContext());
  }

  /**
   * Create a @{link Node}.
   *
   * @param nodeName The name that will identify this node in a ROS2 graph.
   * @param namespace The namespace of the node.
   * @return A @{link Node} that represents the underlying ROS2 node
   *     structure.
   */
  public static Node createNode(final String nodeName, final String namespace, final Context context) {
    long nodeHandle = nativeCreateNodeHandle(nodeName, namespace, context.getHandle());
    Node node = new NodeImpl(nodeHandle, nodeName, context);
    nodes.add(node);
    return node;
  }

  public static void spin(final Node node) {
    ComposableNode composableNode = new ComposableNode() {
      public Node getNode() {
        return node;
      }
    };
    getGlobalExecutor().addNode(composableNode);
    getGlobalExecutor().spin();
    getGlobalExecutor().removeNode(composableNode);
  }

  public static void spin(final ComposableNode composableNode) {
    getGlobalExecutor().addNode(composableNode);
    getGlobalExecutor().spin();
    getGlobalExecutor().removeNode(composableNode);
  }

  public static void spinOnce(final Node node) {
    ComposableNode composableNode = new ComposableNode() {
      public Node getNode() {
        return node;
      }
    };
    getGlobalExecutor().addNode(composableNode);
    getGlobalExecutor().spinOnce();
    getGlobalExecutor().removeNode(composableNode);
  }

  public static void spinOnce(final ComposableNode composableNode) {
    getGlobalExecutor().addNode(composableNode);
    getGlobalExecutor().spinOnce();
    getGlobalExecutor().removeNode(composableNode);
  }

  public static void spinSome(final Node node) {
    ComposableNode composableNode = new ComposableNode() {
      public Node getNode() {
        return node;
      }
    };
    getGlobalExecutor().addNode(composableNode);
    getGlobalExecutor().spinSome();
    getGlobalExecutor().removeNode(composableNode);
  }

  public static void spinSome(final ComposableNode composableNode) {
    getGlobalExecutor().addNode(composableNode);
    getGlobalExecutor().spinSome();
    getGlobalExecutor().removeNode(composableNode);
  }

  public static synchronized void shutdown() {
    cleanup();
    if (RCLJava.defaultContext != null) {
      RCLJava.defaultContext.dispose();
      RCLJava.defaultContext = null;
    }
  }

  public static long convertQoSProfileToHandle(final QoSProfile qosProfile) {
    int history = qosProfile.getHistory().getValue();
    int depth = qosProfile.getDepth();
    int reliability = qosProfile.getReliability().getValue();
    int durability = qosProfile.getDurability().getValue();
    boolean avoidROSNamespaceConventions = qosProfile.getAvoidROSNamespaceConventions();

    return nativeConvertQoSProfileToHandle(
        history, depth, reliability, durability, avoidROSNamespaceConventions);
  }

  private static native long nativeConvertQoSProfileToHandle(int history, int depth,
      int reliability, int durability, boolean avoidROSNamespaceConventions);

  public static void disposeQoSProfile(final long qosProfileHandle) {
    nativeDisposeQoSProfile(qosProfileHandle);
  }

  private static native void nativeDisposeQoSProfile(long qosProfileHandle);
}
