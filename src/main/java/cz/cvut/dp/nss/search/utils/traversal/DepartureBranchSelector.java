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
            final TraversalBranch next = current.next(expander, metadata);
            if(next != null) {
                final Node startNode = next.startNode();
                final Node endNode = next.endNode();

                //cas vyjezdu na teto ceste
                final long departureTime = (long) startNode.getProperty(StopTimeNode.DEPARTURE_PROPERTY);
                //currentTime je arrival, pokud existuje, jinak departure. jde o koncovy uzel na aktualni ceste
                final Long currentNodeArrival = endNode.hasProperty(StopTimeNode.ARRIVAL_PROPERTY) ? (long) endNode.getProperty(StopTimeNode.ARRIVAL_PROPERTY) : null;
                final Long currentNodeDeparture = endNode.hasProperty(StopTimeNode.DEPARTURE_PROPERTY) ? (long) endNode.getProperty(StopTimeNode.DEPARTURE_PROPERTY) : null;

                //zjisti cas uplne prvniho zpracovavaneho uzlu (v ramci vsech cest, nejen teto)
                if(globalFirstNodeDeparture == null) {
                    globalFirstNodeDeparture = departureTime;
                }

                final long currentTime;
                if(currentNodeArrival != null && startNode.getId() != endNode.getId() && !(currentNodeArrival < globalFirstNodeDeparture && currentNodeDeparture != null && currentNodeDeparture > globalFirstNodeDeparture)) {
                    currentTime = currentNodeArrival;
                } else {
                    currentTime = currentNodeDeparture;
                }

                //zjistim, zda jsem se prehoupl pres pulnoc od uplne prvniho zpracovavaneho uzlu
                final boolean overMidnight;
                if(globalFirstNodeDeparture <= currentTime) {
                    overMidnight = false;
                } else {
                    overMidnight = true;
                }

                //zjistim delku aktualni cesty (start-end) ve vterinach
                long travelTime;
                if(departureTime <= currentTime) {
                    travelTime = currentTime - departureTime;
                } else {
                    travelTime = DateTimeUtils.SECONDS_IN_DAY - departureTime + currentTime;
                }

                //zjistim pocet prestupu na aktualni ceste, k tomu musim projit vsechny hrany mezi start a end a hledat prestupni
                int numOfTransfers = 0;
                RelationshipType prevRelationShipType = null;
                for(Relationship relationship : next.reverseRelationships()) {
                    final boolean relationshipIsTypeNextAwaitingStop = relationship.isType(StopTimeNode.REL_NEXT_AWAITING_STOP);
                    //sel jsem (N)-[NEXT_AWAITING_STOP]-(m)-[NEXT_STOP]-(o); tzn prestoupil jsem na jiny trip
                    if(prevRelationShipType != null && relationshipIsTypeNextAwaitingStop && prevRelationShipType.equals(StopTimeNode.REL_NEXT_STOP)) {
                        numOfTransfers++;
                    }

                    //a do dalsi iterace si urcim prevRelatinshipType, coz je ten aktualni
                    if(relationshipIsTypeNextAwaitingStop) {
                        prevRelationShipType = StopTimeNode.REL_NEXT_AWAITING_STOP;
                    } else {
                        prevRelationShipType = StopTimeNode.REL_NEXT_STOP;
                    }
                }

                //celkovy cas jizdy zvysim o penalizace za prestupy
                travelTime = travelTime + (numOfTransfers * DateTimeUtils.TRANSFER_PENALTY_SECONDS);

                //a do fronty pridam aktualni cestu
                queue.addPath(next, currentTime, travelTime, overMidnight);
                result = next;
            } else {
                //vytahnu dalsi prvek z fronty
                current = queue.poll();
                if(current == null) {
                    return null;
                }
            }

        }

        return result;
    }

}
