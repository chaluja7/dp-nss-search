package cz.cvut.dp.nss.search.utils.traversal;

import cz.cvut.dp.nss.search.entity.stopTime.StopTimeNode;
import cz.cvut.dp.nss.search.utils.DateTimeUtils;
import cz.cvut.dp.nss.search.utils.traversal.wrapper.CustomPriorityQueue;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.PathExpander;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.traversal.BranchSelector;
import org.neo4j.graphdb.traversal.TraversalBranch;
import org.neo4j.graphdb.traversal.TraversalContext;

/**
 * Departure branch selector implementation.
 *
 * @author jakubchalupa
 * @since 23.04.15 - 12.02.17
 */
public class DepartureBranchSelector implements BranchSelector {

    private final CustomPriorityQueue queue = new CustomPriorityQueue(FindingType.DEPARTURE);

    private TraversalBranch current;

    private final PathExpander expander;

    private Long globalFirstNodeDeparture;

    public DepartureBranchSelector(TraversalBranch startSource, PathExpander expander) {
        this.current = startSource;
        this.expander = expander;
    }

    @Override
    public TraversalBranch next(TraversalContext metadata) {
        TraversalBranch result = null;

        while(result == null) {
            TraversalBranch next = current.next(expander, metadata);
            if(next != null) {
                Node startNode = next.startNode();
                Node endNode = next.endNode();

                long departureTime = (long) startNode.getProperty(StopTimeNode.DEPARTURE_PROPERTY);
                long currentTime;
                if(endNode.hasProperty(StopTimeNode.ARRIVAL_PROPERTY)) {
                    currentTime = (long) endNode.getProperty(StopTimeNode.ARRIVAL_PROPERTY);
                } else {
                    currentTime = (long) endNode.getProperty(StopTimeNode.DEPARTURE_PROPERTY);
                }

                //zjisti cas uplne prvniho zpracovavaneho uzlu
                if(globalFirstNodeDeparture == null) {
                    globalFirstNodeDeparture = departureTime;
                }

                boolean overMidnight;
                if(globalFirstNodeDeparture <= currentTime) {
                    overMidnight = false;
                } else {
                    overMidnight = true;
                }

                long travelTime;
                if(departureTime <= currentTime) {
                    travelTime = currentTime - departureTime;
                } else {
                    travelTime = DateTimeUtils.SECONDS_IN_DAY - departureTime + currentTime;
                }

                int numOfTransfers = 0;
                RelationshipType prevRelationShipType = null;
                for(Relationship relationship : next.reverseRelationships()) {
                    boolean relationshipIsTypeNextAwaitingStop = relationship.isType(StopTimeNode.REL_NEXT_AWAITING_STOP);
                    //sel jsem (N)-[NEXT_AWAITING_STOP]-(m)-[NEXT_STOP]-(o)
                    if(prevRelationShipType != null && relationshipIsTypeNextAwaitingStop && prevRelationShipType.equals(StopTimeNode.REL_NEXT_STOP)) {
                        numOfTransfers++;
                    }

                    if(relationshipIsTypeNextAwaitingStop) {
                        prevRelationShipType = StopTimeNode.REL_NEXT_AWAITING_STOP;
                    } else {
                        prevRelationShipType = StopTimeNode.REL_NEXT_STOP;
                    }
                }

                travelTime = travelTime + (numOfTransfers * DateTimeUtils.TRANSFER_PENALTY_SECONDS);

                queue.addPath(next, currentTime, travelTime, overMidnight);
                result = next;
            } else {
                current = queue.poll();
                if(current == null) {
                    return null;
                }
            }

        }

        return result;
    }

}
