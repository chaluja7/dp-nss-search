package cz.cvut.dp.nss.search;

import java.util.List;

/**
 * Wrapper to wrap search results.
 * Atributy musi byt public, protoze jinak neo4j nedokaze do nich vlozit hodnoty
 *
 * @author jakubchalupa
 * @since 06.12.14
 */
public class SearchResultWrapper {

    public long travelTime;

    public long departure;

    public long arrival;

    public boolean overMidnightArrival;

    public long numberOfTransfers;

    public List<Long> stops;

    public List<String> stopDetails;

    public long getTravelTime() {
        return travelTime;
    }

    public void setTravelTime(long travelTime) {
        this.travelTime = travelTime;
    }

    public long getDeparture() {
        return departure;
    }

    public void setDeparture(long departure) {
        this.departure = departure;
    }

    public long getArrival() {
        return arrival;
    }

    public void setArrival(long arrival) {
        this.arrival = arrival;
    }

    public boolean isOverMidnightArrival() {
        return overMidnightArrival;
    }

    public void setOverMidnightArrival(boolean overMidnightArrival) {
        this.overMidnightArrival = overMidnightArrival;
    }

    public long getNumberOfTransfers() {
        return numberOfTransfers;
    }

    public void setNumberOfTransfers(long numberOfTransfers) {
        this.numberOfTransfers = numberOfTransfers;
    }

    public List<Long> getStops() {
        return stops;
    }

    public void setStops(List<Long> stops) {
        this.stops = stops;
    }

    public List<String> getStopDetails() {
        return stopDetails;
    }

    public void setStopDetails(List<String> stopDetails) {
        this.stopDetails = stopDetails;
    }
}
