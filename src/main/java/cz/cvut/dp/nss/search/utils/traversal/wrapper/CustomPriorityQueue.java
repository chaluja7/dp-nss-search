package cz.cvut.dp.nss.search.utils.traversal.wrapper;

import cz.cvut.dp.nss.search.utils.traversal.FindingType;
import cz.cvut.dp.nss.search.utils.traversal.comparator.TraversalBranchWrapperArrivalComparator;
import cz.cvut.dp.nss.search.utils.traversal.comparator.TraversalBranchWrapperDepartureComparator;
import org.neo4j.graphdb.traversal.TraversalBranch;

import java.util.PriorityQueue;

/**
 * Priority queue
 *
 * @author jakubchalupa
 * @since 01.05.15
 */
public class CustomPriorityQueue {

    private final PriorityQueue<TraversalBranchWrapper> priorityQueue;

    public CustomPriorityQueue(FindingType findingType) {
        if(findingType == null || findingType.equals(FindingType.DEPARTURE)) {
            priorityQueue = new PriorityQueue<>(new TraversalBranchWrapperDepartureComparator());
        } else {
            priorityQueue = new PriorityQueue<>(new TraversalBranchWrapperArrivalComparator());
        }
    }

    public void addPath(TraversalBranch path, long nodeTime, long travelTime, boolean overMidnight) {
        priorityQueue.add(new TraversalBranchWrapper(path, nodeTime, travelTime, overMidnight));
    }

    public TraversalBranch poll() {
        if(priorityQueue.isEmpty()) {
            return null;
        }

        return priorityQueue.poll().getTraversalBranch();
    }

}
