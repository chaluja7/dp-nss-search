package cz.cvut.dp.nss.search.utils.traversal;

import cz.cvut.dp.nss.search.entity.stopTime.StopTimeNode;
import cz.cvut.dp.nss.search.utils.DateTimeUtils;
import cz.cvut.dp.nss.search.utils.traversal.wrapper.CustomPriorityQueue;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.PathExpander;
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

    private final String stopToName;

    private Long globalFirstNodeDeparture;

    public DepartureBranchSelector(TraversalBranch startSource, PathExpander expander, String stopToName) {
        this.current = startSource;
        this.expander = expander;
        this.stopToName = stopToName;
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
                final String currentStopName = (String) endNode.getProperty(StopTimeNode.STOP_NAME_PROPERTY);

                //zjisti cas uplne prvniho zpracovavaneho uzlu (v ramci vsech cest, nejen teto)
                if(globalFirstNodeDeparture == null) {
                    globalFirstNodeDeparture = departureTime;
                }

                //zajima me departure time pokud existuje. Vyjimka je, pokud jsem na cilove stanici
                //v tom pripade beru arrival (departure uz me zajimat nebude, protoze travezrovani skonci)
                final long currentTime;
                if(currentNodeDeparture == null || currentStopName.equals(stopToName)) {
                    currentTime = currentNodeArrival;
                } else {
                    currentTime = currentNodeDeparture;
                }

                //zjistim, zda jsem se prehoupl pres pulnoc od uplne prvniho zpracovavaneho uzlu
                final boolean overMidnight = globalFirstNodeDeparture > currentTime;

                //zjistim delku aktualni cesty (start-end) ve vterinach
                final long travelTime;
                if(departureTime <= currentTime) {
                    travelTime = currentTime - departureTime;
                } else {
                    travelTime = DateTimeUtils.SECONDS_IN_DAY - departureTime + currentTime;
                }

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
