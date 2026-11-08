package com.apollographql.federation.graphqljava.tracing;

import graphql.execution.ResultPath;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import mdg.engine.proto.Reports;
import org.jetbrains.annotations.NotNull;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Benchmark for {@code FederatedTracingState.ProtoBuilderTree}: the path-keyed map and read-write
 * lock implementation it replaced ({@link OldTree}) against the trie ({@link NewTree}).
 *
 * <p>The shape is an {@code _entities} response with 1,500 list items under {@code
 * /_entities/0/children}, each with two leaf fields. Both trees are copied here as private fixtures
 * because the real class is private. {@link NewTree} must be kept identical to {@code
 * FederatedTracingInstrumentation.FederatedTracingState.ProtoBuilderTree}.
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
    NewTree tree = new NewTree();
    tree.editBuilder(ENTITIES, SET_ANCESTOR);
    tree.editBuilder(CHILDREN, SET_ANCESTOR);
    for (ResultPath path : PATHS) {
      tree.editBuilder(path, b -> setField(b, path));
    }
    return tree.toProto();
  }

  // ---------------------------------------------------------------------------------------
  // Eight threads sharing one tree: one op builds a complete tree, with the item edits split
  // into eight slices that run concurrently on a fixed pool of eight threads, as field fetch
  // callbacks do under AsyncExecutionStrategy. Every field edit is a first write (a map or trie
  // miss), as it is in a real request. Task submission and the wait for completion cost the
  // same for both trees.
  // ---------------------------------------------------------------------------------------

  @State(Scope.Benchmark)
  public static class Pool {
    ExecutorService executor;

    @Setup(Level.Trial)
    public void setup() {
      executor = Executors.newFixedThreadPool(THREADS);
    }

    @TearDown(Level.Trial)
    public void tearDown() {
      executor.shutdownNow();
    }
  }

  static final int PER_SLICE = ((ITEMS + THREADS - 1) / THREADS) * FIELDS.length;

  @Benchmark
  public Reports.Trace.Node oldTreeEightThreads(Pool pool) throws InterruptedException {
    OldTree tree = new OldTree();
    tree.editBuilder(ENTITIES, SET_ANCESTOR);
    tree.editBuilder(CHILDREN, SET_ANCESTOR);
    runSlices(pool.executor, (path) -> tree.editBuilder(path, b -> setField(b, path)));
    return tree.toProto();
  }

  @Benchmark
  public Reports.Trace.Node newTreeEightThreads(Pool pool) throws InterruptedException {
    NewTree tree = new NewTree();
    tree.editBuilder(ENTITIES, SET_ANCESTOR);
    tree.editBuilder(CHILDREN, SET_ANCESTOR);
    runSlices(pool.executor, (path) -> tree.editBuilder(path, b -> setField(b, path)));
    return tree.toProto();
  }

  /** Applies {@code edit} to every path, in {@link #THREADS} concurrent slices. */
  private static void runSlices(ExecutorService executor, Consumer<ResultPath> edit)
      throws InterruptedException {
    CountDownLatch done = new CountDownLatch(THREADS);
    AtomicReference<Throwable> failure = new AtomicReference<>();
    for (int t = 0; t < THREADS; t++) {
      int from = t * PER_SLICE;
      int to = Math.min(PATHS.length, from + PER_SLICE);
      executor.execute(
          () -> {
            try {
              for (int i = from; i < to; i++) {
                edit.accept(PATHS[i]);
              }
            } catch (Throwable e) {
              failure.compareAndSet(null, e);
            } finally {
              done.countDown();
            }
          });
    }
    done.await();
    if (failure.get() != null) {
      throw new IllegalStateException(failure.get());
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

  /** Copy of the current implementation: trie. Keep in sync with the real class. */
  private static class NewTree {
    private final Node root;
    // This flag is used to ensure protobuf conversion occurs once, and that builders can't
    // be edited afterwards. We intentionally leave this field to be default initialized as
    // false.
    //
    // There is no tree-wide lock. Edits never contend with each other: each field node's
    // builder is written only by that field's own fetch completion callback; index
    // placeholder nodes are written once, at creation, before being published through
    // putIfAbsent(); and root errors are added from beginParse() and beginValidation(),
    // which complete before execution starts.
    //
    // Edits can, however, overlap toProto(). graphql-java normally completes the execution
    // result (and so calls toProto()) only after every field fetch has completed, but that
    // is not guaranteed: for example, when the ExecutionInput is cancelled the result is
    // completed while fetches may still be in flight, and their completion callbacks then
    // call editBuilder() late. To keep such an edit from mutating a builder while build()
    // reads it, toProto() sets this flag before building anything and builds each node
    // inside that node's builder monitor, and editBuilder() re-checks the flag inside the
    // same monitor. Those monitors are per-node and uncontended, so they add no shared
    // state to the edit path; the tree-wide lock this replaces was the contended part.
    private volatile boolean finalized;

    public NewTree() {
      root = new Node(Reports.Trace.Node.newBuilder());
    }

    /** Edit builder for the node at the given path (creating it and its parents if needed). */
    public void editBuilder(ResultPath path, Consumer<Reports.Trace.Node.Builder> builderConsumer) {
      // Checked early so that an edit arriving after conversion does not grow the trie.
      checkNotFinalized();
      Node node = getOrCreateNode(path, true);
      synchronized (node.builder) {
        checkNotFinalized();
        builderConsumer.accept(node.builder);
      }
    }

    private void checkNotFinalized() {
      if (finalized) {
        throw new RuntimeException("Cannot edit builder after protobuf conversion.");
      }
    }

    /**
     * Get node for the given path in the trie (creating it and its parents if needed).
     *
     * <p>This recurses up the path's parent chain to the root, then descends one child lookup per
     * level on the way back. It never materializes intermediate {@link ResultPath} instances.
     *
     * @param isRequested whether {@code path} is the path originally requested, as opposed to one
     *     of its ancestors
     */
    @NotNull
    private Node getOrCreateNode(ResultPath path, boolean isRequested) {
      if (path.isRootPath()) {
        return root;
      }
      Node parent = getOrCreateNode(path.getParent(), false);
      Object segment = path.getSegmentValue();
      Node child = parent.childrenBySegment.get(segment);
      if (child != null) {
        return child;
      }

      Reports.Trace.Node.Builder childBuilder = Reports.Trace.Node.newBuilder();
      if (segment instanceof Integer) {
        childBuilder.setIndex((Integer) segment);
      } else if (!isRequested) {
        // We've encountered a field name node that is an ancestor of the requested
        // node. However, the fetcher for that field name should have ultimately
        // called getOrCreateNode() on its node before getOrCreateNode() was called
        // on the requested node, meaning that this field name node should already
        // be in the trie. Accordingly, we should never encounter such field name
        // ancestor nodes, and we throw when we do.
        throw new RuntimeException("Unexpected missing non-index " + segment);
      }
      Node childNode = new Node(childBuilder);

      // Note that putIfAbsent() here will give the child node if it already existed,
      // or null if it didn't (in which case the node passed to putIfAbsent() becomes
      // the child node). Only the thread that inserted the node adds it to the
      // parent's ordered children list, so each node appears there exactly once.
      child = parent.childrenBySegment.putIfAbsent(segment, childNode);
      if (child == null) {
        child = childNode;
        parent.children.add(childNode);
      }
      return child;
    }

    /**
     * Convert the entire builder tree to protobuf, forming the parent-child relationships as
     * needed.
     *
     * <p>Note that no builder may be edited after this method is called; a late edit throws. This
     * method is synchronized only so that the one-time conversion is idempotent if it is ever
     * called concurrently; it is off the per-field hot path.
     */
    @NotNull
    public synchronized Reports.Trace.Node toProto() {
      if (!finalized) {
        // Set before building so that no edit can enter a builder monitor after we have
        // built that builder (see the comment on this field).
        finalized = true;
        buildDescendants(root);
      }
      synchronized (root.builder) {
        return root.builder.build();
      }
    }

    /**
     * Recursively build the protobuf builder for the given node, forming the parent-child
     * relationships between builders of descendant nodes as needed.
     *
     * <p>Note that {@link #finalized} must be set before calling this method. Each builder is read
     * and written only inside its own monitor, so this waits out any edit that was already in
     * progress when the flag was set.
     */
    private void buildDescendants(Node node) {
      for (Node childNode : node.children) {
        buildDescendants(childNode);
        Reports.Trace.Node child;
        synchronized (childNode.builder) {
          child = childNode.builder.build();
        }
        if (child.getIdCase() == Reports.Trace.Node.IdCase.ID_NOT_SET) {
          // A field node created by an edit that raced this conversion and was then rejected
          // in editBuilder(), so it was never written. Omit it (and anything below it) rather
          // than emitting a node with neither a response name nor an index.
          continue;
        }
        synchronized (node.builder) {
          node.builder.addChild(child);
        }
      }
    }

    private static class Node {
      public final Reports.Trace.Node.Builder builder;
      // Children keyed by result path segment value (String for fields, Integer for list
      // indices), used for lookup.
      public final ConcurrentMap<Object, Node> childrenBySegment;
      // Children in insertion order, used for protobuf output.
      public final ConcurrentLinkedQueue<Node> children;

      public Node(Reports.Trace.Node.Builder builder) {
        this.builder = builder;
        this.childrenBySegment = new ConcurrentHashMap<>();
        this.children = new ConcurrentLinkedQueue<>();
      }
    }
  }
}
