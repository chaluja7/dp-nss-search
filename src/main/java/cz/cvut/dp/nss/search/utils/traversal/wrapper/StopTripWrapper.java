package cz.cvut.dp.nss.search.utils.traversal.wrapper;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Wrapper to wrap stations for traversal purposes.
 *
 * @author jakubchalupa
 * @since 20.04.15 - 12.02.17
 */
public class StopTripWrapper {

    private Set<String> visitedTrips;

    /**
     * id stanice -> id tripu v ramci kterych jsem uz na dane stanici byl
     */
    private Map<String, Set<String>> visitedStops;

    /**
     * cas prijezdu na aktualni stanici (pro zjisteni, jak dlouho uz na nic cekam na prestup)
     */
    private long thisStopArrival;

    public Set<String> getVisitedTrips() {
        if(visitedTrips == null) {
            visitedTrips = new HashSet<>();
        }

        return visitedTrips;
    }

    public void setVisitedTrips(Set<String> visitedTrips) {
        this.visitedTrips = visitedTrips;
    }

    public Map<String, Set<String>> getVisitedStops() {
        if(visitedStops == null) {
            visitedStops = new HashMap<>();
        }

        return visitedStops;
    }

    public void setVisitedStops(Map<String, Set<String>> visitedStops) {
        this.visitedStops = visitedStops;
    }

    public long getThisStopArrival() {
        return thisStopArrival;
    }

    public void setThisStopArrival(long thisStopArrival) {
        this.thisStopArrival = thisStopArrival;
    }
}
