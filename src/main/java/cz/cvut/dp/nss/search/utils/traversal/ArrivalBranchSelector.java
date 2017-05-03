package cz.cvut.dp.nss.search.utils.traversal;

import cz.cvut.dp.nss.search.entity.stopTime.StopTimeNode;
import cz.cvut.dp.nss.search.utils.DateTimeUtils;
import cz.cvut.dp.nss.search.utils.traversal.wrapper.CustomPriorityQueue;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.PathExpander;
import org.neo4j.graphdb.traversal.BranchSelector;
import org.neo4j.graphdb.traversal.TraversalBranch;
import org.neo4j.graphdb.traversal.TraversalContext;

/**
 * Arrival branch selector implementation.
 *
 * @author jakubchalupa
 * @since 23.04.15 - 12.04.17
 */
public class ArrivalBranchSelector implements BranchSelector {

    private final CustomPriorityQueue queue = new CustomPriorityQueue(FindingType.ARRIVAL);

    private TraversalBranch current;

    private final PathExpander expander;

    private final String stopFromName;

    private Long globalFirstNodeArrival;

    public ArrivalBranchSelector(TraversalBranch startSource, PathExpander expander, String stopFromName) {
        this.current = startSource;
        this.expander = expander;
        this.stopFromName = stopFromName;
    }

    @Override
    public TraversalBranch next(TraversalContext metadata) {
        TraversalBranch result = null;

        while(result == null) {
            final TraversalBranch next = current.next(expander, metadata);
            if(next != null) {
                //TODO nema to tady byt naopak?
                final Node startNode = next.startNode();
                Node endNode = next.endNode();

                //TODO toto je hack, protoze potrebuji do evaluatoru dostavat nody ve spravnem poradi, kdyby neco nefungovalo tak toto smazat
                if(endNode.hasRelationship(StopTimeNode.REL_NEXT_STOP, Direction.INCOMING) && !((endNode.getProperty(StopTimeNode.STOP_NAME_PROPERTY)).equals(stopFromName))) {
                    //TODO nevyberu ten samy?
                    endNode = endNode.getSingleRelationship(StopTimeNode.REL_NEXT_STOP, Direction.INCOMING).getStartNode();
                }

                //cas prijezdu na teto ceste
                final long arrivalTime = (long) startNode.getProperty(StopTimeNode.ARRIVAL_PROPERTY);
                //currentTime je departure, pokud existuje, jinak arrival. jde o koncovy uzel na aktualni ceste
                final Long currentNodeArrival = endNode.hasProperty(StopTimeNode.ARRIVAL_PROPERTY) ? (long) endNode.getProperty(StopTimeNode.ARRIVAL_PROPERTY) : null;
                final Long currentNodeDeparture = endNode.hasProperty(StopTimeNode.DEPARTURE_PROPERTY) ? (long) endNode.getProperty(StopTimeNode.DEPARTURE_PROPERTY) : null;
                final String currentStopName = (String) endNode.getProperty(StopTimeNode.STOP_NAME_PROPERTY);

                //zjisti cas uplne prvniho zpracovavaneho uzlu (v ramci vsech cest, nejen teto)
                if(globalFirstNodeArrival == null) {
                    globalFirstNodeArrival = arrivalTime;
                }

                //zajima me arrival time pokud existuje. Vyjimka je, pokud jsem na cilove stanici
                //v tom pripade beru departure (arrival uz me zajimat nebude, protoze travezrovani skonci)
                final long currentTime;
                if(currentNodeArrival == null || currentStopName.equals(stopFromName)) {
                    currentTime = currentNodeDeparture;
                } else {
                    currentTime = currentNodeArrival;
                }

                //zjistim, zda jsem se prehoupl pres pulnoc od uplne prvniho zpracovavaneho uzlu
                final boolean overMidnight = globalFirstNodeArrival < currentTime;

                //zjistim delku aktualni cesty (start-end) ve vterinach
                final long travelTime;
                if(arrivalTime >= currentTime) {
                    travelTime = arrivalTime - currentTime;
                } else {
                    travelTime = DateTimeUtils.SECONDS_IN_DAY - currentTime + arrivalTime;
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
