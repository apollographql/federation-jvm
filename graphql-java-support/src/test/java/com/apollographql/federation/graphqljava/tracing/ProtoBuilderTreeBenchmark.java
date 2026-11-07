package com.apollographql.federation.graphqljava.tracing;

import com.apollographql.federation.graphqljava.tracing.FederatedTracingInstrumentation.FederatedTracingState.ProtoBuilderTree;
import graphql.execution.ResultPath;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import mdg.engine.proto.Reports;
import org.jetbrains.annotations.NotNull;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Group;
import org.openjdk.jmh.annotations.GroupThreads;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Benchmark for {@link ProtoBuilderTree}: the path-keyed map implementation it replaced ({@link
 * OldTree}) against the current trie.
 *
 * <p>The shape is an {@code _entities} response with 1,500 list items under {@code
 * /_entities/0/children}, each with two leaf fields. {@link OldTree} is a copy of the previous
 * implementation kept here as a private fixture.
 *
 * <p>Run with {@code ./gradlew :federation-graphql-java-support:jmh}.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
public class ProtoBuilderTreeBenchmark {
  static final int ITEMS = 1500;
  static final int THREADS = 8;
  static final String[] FIELDS = {"id", "name"};

  static final ResultPath ENTITIES = ResultPath.rootPath().segment("_entities");
  static final ResultPath CHILDREN = ENTITIES.segment(0).segment("children");

  /** Field paths, laid out as {@code paths[item * FIELDS.length + field]}. */
  static final ResultPath[] PATHS = new ResultPath[ITEMS * FIELDS.length];

  static {
    for (int i = 0; i < ITEMS; i++) {
      ResultPath item = CHILDREN.segment(i);
      for (int f = 0; f < FIELDS.length; f++) {
        PATHS[i * FIELDS.length + f] = item.segment(FIELDS[f]);
      }
    }
  }

  static final Consumer<Reports.Trace.Node.Builder> SET_ANCESTOR =
      builder -> builder.setStartTime(1).setEndTime(2).setResponseName("x");

  static void setField(Reports.Trace.Node.Builder builder, ResultPath path) {
    builder
        .setStartTime(1)
        .setEndTime(2)
        .setParentType("Child")
        .setType("String")
        .setResponseName(path.getSegmentName());
  }

  // ---------------------------------------------------------------------------------------
  // Single-threaded: one op builds a complete tree.
  // ---------------------------------------------------------------------------------------

  @Benchmark
  public Reports.Trace.Node oldTreeSingleThread() {
    OldTree tree = new OldTree();
    tree.editBuilder(ENTITIES, SET_ANCESTOR);
    tree.editBuilder(CHILDREN, SET_ANCESTOR);
    for (ResultPath path : PATHS) {
      tree.editBuilder(path, b -> setField(b, path));
    }
    return tree.toProto();
  }

  @Benchmark
  public Reports.Trace.Node newTreeSingleThread() {
    ProtoBuilderTree tree = new ProtoBuilderTree();
    tree.editBuilder(ENTITIES, SET_ANCESTOR);
    tree.editBuilder(CHILDREN, SET_ANCESTOR);
    for (ResultPath path : PATHS) {
      tree.editBuilder(path, b -> setField(b, path));
    }
    return tree.toProto();
  }

  // ---------------------------------------------------------------------------------------
  // Eight threads sharing one tree: each op is one thread writing its 1/8 slice of the items
  // into a tree that is replaced at a barrier before every invocation, so every field edit is
  // a first write (a map or trie miss), as it is in a real request. At the end of an
  // iteration, threads stop arriving at the barrier; the remaining waiters time out, which
  // breaks the barrier, and the barrier is reset before the next iteration. The handful of
  // invocations lost that way is noise against the ~1s iteration.
  // ---------------------------------------------------------------------------------------

  @State(Scope.Benchmark)
  public static class SliceAssigner {
    final AtomicInteger next = new AtomicInteger();
  }

  @State(Scope.Thread)
  public static class Slice {
    int from;
    int to;

    @Setup
    public void setup(SliceAssigner assigner) {
      int slice = assigner.next.getAndIncrement() % THREADS;
      int perThread = (ITEMS + THREADS - 1) / THREADS;
      from = slice * perThread * FIELDS.length;
      to = Math.min(PATHS.length, (slice + 1) * perThread * FIELDS.length);
    }
  }

  @State(Scope.Group)
  public static class SharedOld {
    @Setup(Level.Iteration)
    public void reset() {
      barrier.reset();
    }

    volatile OldTree tree;
    final CyclicBarrier barrier =
        new CyclicBarrier(
            THREADS,
            () -> {
              OldTree t = new OldTree();
              t.editBuilder(ENTITIES, SET_ANCESTOR);
              t.editBuilder(CHILDREN, SET_ANCESTOR);
              tree = t;
            });
  }

  @State(Scope.Group)
  public static class SharedNew {
    @Setup(Level.Iteration)
    public void reset() {
      barrier.reset();
    }

    volatile ProtoBuilderTree tree;
    final CyclicBarrier barrier =
        new CyclicBarrier(
            THREADS,
            () -> {
              ProtoBuilderTree t = new ProtoBuilderTree();
              t.editBuilder(ENTITIES, SET_ANCESTOR);
              t.editBuilder(CHILDREN, SET_ANCESTOR);
              tree = t;
            });
  }

  @Benchmark
  @Group("oldTreeEightThreads")
  @GroupThreads(THREADS)
  public void oldTreeEightThreads(SharedOld shared, Slice slice, Blackhole bh)
      throws InterruptedException {
    if (!awaitOrGiveUp(shared.barrier)) {
      return;
    }
    OldTree tree = shared.tree;
    for (int i = slice.from; i < slice.to; i++) {
      ResultPath path = PATHS[i];
      tree.editBuilder(path, b -> setField(b, path));
    }
    bh.consume(tree);
  }

  @Benchmark
  @Group("newTreeEightThreads")
  @GroupThreads(THREADS)
  public void newTreeEightThreads(SharedNew shared, Slice slice, Blackhole bh)
      throws InterruptedException {
    if (!awaitOrGiveUp(shared.barrier)) {
      return;
    }
    ProtoBuilderTree tree = shared.tree;
    for (int i = slice.from; i < slice.to; i++) {
      ResultPath path = PATHS[i];
      tree.editBuilder(path, b -> setField(b, path));
    }
    bh.consume(tree);
  }

  private static boolean awaitOrGiveUp(CyclicBarrier barrier) throws InterruptedException {
    try {
      barrier.await(200, TimeUnit.MILLISECONDS);
      return true;
    } catch (TimeoutException | BrokenBarrierException e) {
      // Other group threads have finished the iteration.
      return false;
    }
  }

  // ---------------------------------------------------------------------------------------
  // Fixtures.
  // ---------------------------------------------------------------------------------------

  /** The implementation prior to this change: path-keyed map plus read-write lock. */
  private static class OldTree {
    private final Node root;
    private final ConcurrentMap<ResultPath, Node> nodesByPath;
    // We use a whole-tree read-write lock to prevent the protobuf conversion step from
    // having to acquire each node's lock.
    private final ReadWriteLock treeLock;
    // This flag is used to ensure protobuf conversion occurs once, and that builders can't
    // be edited afterwards. We intentionally leave this field to be default initialized as
    // false.
    private boolean isFinalized;

    public OldTree() {
      root = new Node(Reports.Trace.Node.newBuilder());
      nodesByPath = new ConcurrentHashMap<>();
      nodesByPath.put(ResultPath.rootPath(), root);
      treeLock = new ReentrantReadWriteLock();
    }

    /** Edit builder for the node at the given path (creating it and its parents if needed). */
    public void editBuilder(ResultPath path, Consumer<Reports.Trace.Node.Builder> builderConsumer) {
      Lock l = treeLock.readLock();
      l.lock();
      try {
        if (isFinalized) {
          throw new RuntimeException("Cannot edit builder after protobuf conversion.");
        }
        Node node = getOrCreateNode(path);
        synchronized (node.builder) {
          builderConsumer.accept(node.builder);
        }
      } finally {
        l.unlock();
      }
    }

    /**
     * Get node for the given path in nodesByPath (creating it and its parents if needed).
     *
     * <p>Note that {@link #treeLock}'s read lock must be held when calling this method.
     */
    @NotNull
    private Node getOrCreateNode(ResultPath path) {
      // Fast path for when the node already exists.
      Node current = nodesByPath.get(path);
      if (current != null) {
        return current;
      }

      // Find the latest ancestor that exists in the map.
      List<Object> pathSegments = path.toList();
      int currentSegmentIndex = pathSegments.size();
      while (current == null) {
        if (currentSegmentIndex <= 0) {
          // The root path's node is inserted at construction time, so this shouldn't
          // happen.
          throw new RuntimeException("root path missing from nodesByPath?");
        }
        currentSegmentIndex--;
        ResultPath currentPath = ResultPath.fromList(pathSegments.subList(0, currentSegmentIndex));
        current = nodesByPath.get(currentPath);
      }

      // Travel back down to the requested node, creating child nodes along the way as
      // needed.
      for (; currentSegmentIndex < pathSegments.size(); currentSegmentIndex++) {
        Node parent = current;
        ResultPath childPath =
            ResultPath.fromList(pathSegments.subList(0, currentSegmentIndex + 1));
        Object childSegment = pathSegments.get(currentSegmentIndex);

        Reports.Trace.Node.Builder childBuilder = Reports.Trace.Node.newBuilder();
        if (childSegment instanceof Integer) {
          childBuilder.setIndex((Integer) childSegment);
        } else if (currentSegmentIndex < pathSegments.size() - 1) {
          // We've encountered a field name node that is an ancestor of the requested
          // node. However, the fetcher for that field name should have ultimately
          // called getOrCreateNode() on its node before getOrCreateNode() was called
          // on the requested node, meaning that this field name node should already
          // be in nodesByPath. Accordingly, we should never encounter such field
          // name ancestor nodes, and we throw when we do.
          throw new RuntimeException("Unexpected missing non-index " + childSegment);
        }
        Node childNode = new Node(childBuilder);

        // Note that putIfAbsent() here will give the child node if it already existed,
        // or null if it didn't (in which case the node passed to putIfAbsent() becomes
        // the child node).
        current = nodesByPath.putIfAbsent(childPath, childNode);
        if (current == null) {
          current = childNode;
          parent.children.add(childNode);
        }
      }

      return current;
    }

    /**
     * Convert the entire builder tree to protobuf, forming the parent-child relationships as
     * needed.
     *
     * <p>Note that no builder may be edited after this method is called.
     */
    @NotNull
    public Reports.Trace.Node toProto() {
      Lock l = treeLock.writeLock();
      l.lock();
      try {
        if (!isFinalized) {
          buildDescendants(root);
          isFinalized = true;
        }
        return root.builder.build();
      } finally {
        l.unlock();
      }
    }

    /**
     * Recursively build the protobuf builder for the given node, forming the parent-child
     * relationships between builders of descendant nodes as needed.
     *
     * <p>Note that {@link #treeLock}'s write lock must be held when calling this method.
     */
    private void buildDescendants(Node node) {
      for (Node childNode : node.children) {
        buildDescendants(childNode);
        node.builder.addChild(childNode.builder.build());
      }
    }

    private static class Node {
      public final Reports.Trace.Node.Builder builder;
      public final ConcurrentLinkedQueue<Node> children;

      public Node(Reports.Trace.Node.Builder builder) {
        this.builder = builder;
        this.children = new ConcurrentLinkedQueue<>();
      }
    }
  }
}
