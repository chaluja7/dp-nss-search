package cz.cvut.dp.nss.search.utils.traversal.wrapper;

import java.util.*;

/**
 * Wrapper to wrap stations for traversal purposes.
 *
 * @author jakubchalupa
 * @since 20.04.15 - 12.02.17
 */
public class StopTripWrapper {

    private Set<String> visitedTrips;

    private Map<String, List<String>> visitedStops;

    public Set<String> getVisitedTrips() {
        if(visitedTrips == null) {
            visitedTrips = new HashSet<>();
        }

        return visitedTrips;
    }

    public void setVisitedTrips(Set<String> visitedTrips) {
        this.visitedTrips = visitedTrips;
    }

    public Map<String, List<String>> getVisitedStops() {
        if(visitedStops == null) {
            visitedStops = new HashMap<>();
        }

        return visitedStops;
    }

    public void setVisitedStops(Map<String, List<String>> visitedStops) {
        this.visitedStops = visitedStops;
    }
}
