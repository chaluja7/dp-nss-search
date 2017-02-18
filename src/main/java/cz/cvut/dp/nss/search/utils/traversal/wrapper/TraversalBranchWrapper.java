package cz.cvut.dp.nss.search.utils.traversal.wrapper;

import org.neo4j.graphdb.traversal.TraversalBranch;

/**
 * Traversal branch wrapper. Wraps traversal branch for priority queue.
 *
 * @author jakubchalupa
 * @since 13.05.15 - 12.02.17
 */
public class TraversalBranchWrapper {

    private TraversalBranch traversalBranch;

    private long nodeTime;

    private long travelTime;

    private boolean overMidnight;

    public TraversalBranchWrapper(TraversalBranch traversalBranch, long nodeTime, long travelTime, boolean overMidnight) {
        this.traversalBranch = traversalBranch;
        this.nodeTime = nodeTime;
        this.travelTime = travelTime;
        this.overMidnight = overMidnight;
    }

    public TraversalBranch getTraversalBranch() {
        return traversalBranch;
    }

    public void setTraversalBranch(TraversalBranch traversalBranch) {
        this.traversalBranch = traversalBranch;
    }

    public long getNodeTime() {
        return nodeTime;
    }

    public void setNodeTime(long nodeTime) {
        this.nodeTime = nodeTime;
    }

    public long getTravelTime() {
        return travelTime;
    }

    public void setTravelTime(long travelTime) {
        this.travelTime = travelTime;
    }

    public boolean isOverMidnight() {
        return overMidnight;
    }

    public void setOverMidnight(boolean overMidnight) {
        this.overMidnight = overMidnight;
    }
}
