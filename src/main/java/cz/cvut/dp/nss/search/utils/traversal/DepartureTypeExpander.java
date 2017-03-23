package cz.cvut.dp.nss.search.utils.traversal;

import cz.cvut.dp.nss.search.entity.calendar.CalendarNode;
import cz.cvut.dp.nss.search.entity.stopTime.StopTimeNode;
import cz.cvut.dp.nss.search.entity.trip.TripNode;
import cz.cvut.dp.nss.search.utils.CollectionCloneUtils;
import cz.cvut.dp.nss.search.utils.DateTimeUtils;
import cz.cvut.dp.nss.search.utils.traversal.wrapper.StopTripWrapper;
import org.joda.time.LocalDateTime;
import org.neo4j.graphdb.*;
import org.neo4j.graphdb.traversal.BranchState;
import org.neo4j.helpers.collection.Iterables;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Departure type expander implementation.
 *
 * @author jakubchalupa
 * @since 19.04.15 - 12.02.17
 */
public class DepartureTypeExpander implements PathExpander<StopTripWrapper> {

    private final LocalDateTime departureDateTime;

    private final int maxNumberOfTransfers;

    private final int departureDayOfYear;

    private final int maxDepartureDayOfYear;

    private final int departureSecondsOfDay;

    private final int maxDepartureSecondsOfDay;

    /**
     * calendarId -> calendarNode. Je to mapa s calendar pro zjistovani platnosti prochazenych uzlu
     */
    private final Map<String, CalendarNode> calendarNodeMap;

    /**
     * id stopTime -> nejlepsi cas (delka cesty od zacatku), ve ktere jsem na nem zatim byl v ramci hledani
     */
    private final Map<Long, Long> visitedStops = new HashMap<>();

    public DepartureTypeExpander(final LocalDateTime departureDateTime, final LocalDateTime maxDepartureDateTime,
                                 final int maxNumberOfTransfers, final Map<String, CalendarNode> calendarNodeMap) {
        this.departureDateTime = departureDateTime;
        this.maxNumberOfTransfers = maxNumberOfTransfers;
        this.calendarNodeMap = calendarNodeMap;

        this.departureDayOfYear = departureDateTime.getDayOfYear();
        this.maxDepartureDayOfYear = maxDepartureDateTime.getDayOfYear();
        this.departureSecondsOfDay = departureDateTime.getMillisOfDay() / 1000;
        this.maxDepartureSecondsOfDay = maxDepartureDateTime.getMillisOfDay() / 1000;
    }

    @Override
    public Iterable<Relationship> expand(Path path, BranchState<StopTripWrapper> branchState) {

        //Predavani parametru PATH, pokazde musim vytvorit nove instance, aby se jednotlive path neovlivnovali
        final StopTripWrapper stopTripWrapperOld = branchState.getState();
        //jmeno stanice -> id tripu, na kterych jsem jiz stanici navstivil
        final Map<String, Set<String>> visitedStops = CollectionCloneUtils.cloneMap(stopTripWrapperOld.getVisitedStops());
        //id tripu, ktere jsem jiz navstivil
        final Set<String> visitedTrips = CollectionCloneUtils.cloneSet(stopTripWrapperOld.getVisitedTrips());

        //vytvoreni nove instance stopTripWrapperu
        final StopTripWrapper stopTripWrapper = new StopTripWrapper();
        stopTripWrapper.setVisitedStops(visitedStops);
        stopTripWrapper.setVisitedTrips(visitedTrips);
        stopTripWrapper.setThisStopArrival(stopTripWrapperOld.getThisStopArrival());
        branchState.setState(stopTripWrapper);

        //inicializace parametru PATH
        final Node startNode = path.startNode();
        final Node currentNode = path.endNode();
        final String startNodeStopName = (String) startNode.getProperty(StopTimeNode.STOP_NAME_PROPERTY);
        final long startNodeDeparture = (long) startNode.getProperty(StopTimeNode.DEPARTURE_PROPERTY);
        final long currentNodeStopTimeId = (long) currentNode.getProperty(StopTimeNode.STOP_TIME_ID_PROPERTY);
        final String currentTripId = (String) currentNode.getProperty(StopTimeNode.TRIP_PROPERTY);
        final String currentStopName = (String) currentNode.getProperty(StopTimeNode.STOP_NAME_PROPERTY);
        final boolean currentStopIsOverMidnightInTrip = (boolean) currentNode.getProperty(StopTimeNode.OVER_MIDNIGHT_PROPERTY);
        final Relationship lastRelationShip = path.lastRelationship();

        //rozhodujici je departureTime pokud existuje, jinak arrival time
        Long currentNodeArrival = null;
        Long currentNodeDeparture = null;
        if(currentNode.hasProperty(StopTimeNode.DEPARTURE_PROPERTY)) {
            currentNodeDeparture = (Long) currentNode.getProperty(StopTimeNode.DEPARTURE_PROPERTY);
        }
        if(currentNode.hasProperty(StopTimeNode.ARRIVAL_PROPERTY)) {
            currentNodeArrival = (Long) currentNode.getProperty(StopTimeNode.ARRIVAL_PROPERTY);
        }

        //node musi mit alespon jeden z casu arrival/departure
        if(currentNodeArrival == null && currentNodeDeparture == null) {
            throw new RuntimeException();
        }

        final long currentNodeTimeProperty = currentNodeDeparture != null ? currentNodeDeparture : currentNodeArrival;
        final long inverseCurrentNodeTimeProperty = currentNodeArrival != null ? currentNodeArrival : currentNodeDeparture;

        //spocitani aktualniho travel time - od zacatku cesty do nynejsiho uzlu
        long travelTime;
        if(inverseCurrentNodeTimeProperty >= startNodeDeparture) {
            //v ramci dne
            travelTime = inverseCurrentNodeTimeProperty - startNodeDeparture;
        } else {
            //prehoupl jsem se pres pulnoc
            travelTime = DateTimeUtils.SECONDS_IN_DAY - startNodeDeparture + inverseCurrentNodeTimeProperty;
        }
        //cas cesty s penalizacemi za prestup
        final long travelTimeWithPenalty = travelTime + ((visitedTrips.size() - 1) * DateTimeUtils.TRANSFER_PENALTY_SECONDS);

        //Jsem na prvnim NODu
        if(lastRelationShip == null) {
            final Set<String> tmpVisitedTrips = new HashSet<>();
            tmpVisitedTrips.add(currentTripId);

            //a do path parametru pridam potrebne info (navstiveny trip a stanice)
            visitedStops.put(currentStopName, tmpVisitedTrips);
            visitedTrips.add(currentTripId);
            this.visitedStops.put(currentNodeStopTimeId, travelTimeWithPenalty);

            //vratit chci z prvniho nodu jen node na NEXT_STOP relaci (z prvniho uzlu nechci prestupovat)
            return currentNode.getRelationships(Direction.OUTGOING, StopTimeNode.REL_NEXT_STOP);
        }

        //pokud jsem se dostal zpet do startovni stanice tak je smycka, coz nechci
        if(currentStopName.equals(startNodeStopName)) {
            return Iterables.empty();
        }

        //TODO pozor, zde maxDeparture znasilnuji k ucelu, ke kteremu neslouzi! maxDeparture by melo znacit maxialni cas
        //TODO pouze prvniho uzlu, ne jakehokoliv po ceste
        //musim zkonrolovat, zda aktualni node nema time jiz po case maxDepartureTime
        if(departureDayOfYear == maxDepartureDayOfYear) {
            //pohybuji se v ramci jednoho dne
            if(currentNodeTimeProperty < departureSecondsOfDay || currentNodeTimeProperty > maxDepartureSecondsOfDay) {
                //presahl jsem casovy rozsah vyhledavani (prehoupl jsem se pres pulnoc, ackoliv jsem nemel nebo jsem presahl maxDeparture)
                return Iterables.empty();
            }
        } else if((departureDayOfYear == maxDepartureDayOfYear - 1) || (maxDepartureDayOfYear == 1 && (departureDayOfYear == 365 || departureDayOfYear == 366))) {
            //prehoupl jsem se pres pulnoc, vyresil jsem i prechod pres pulnoc mezi roky
            if(currentNodeTimeProperty < departureSecondsOfDay && currentNodeTimeProperty > maxDepartureSecondsOfDay) {
                //presahl jsem casovy rozsah vyhledavani
                return Iterables.empty();
            }
        } else {
            //K tomu by nemelo dojit, neumime vyhledavat pres vice nez 24 hodin
            throw new RuntimeException("vyhledavani musi skoncit nejdele po 24 hodinach");
        }

        //Posledni hrana byla cekaci
        if(lastRelationShip.isType(StopTimeNode.REL_NEXT_AWAITING_STOP)) {
            //zjistim, jestli uz muzu na tento spoj prestoupit vzhledem k minimalnimu poctu minut na prestup
            final long thisStopArrival = stopTripWrapperOld.getThisStopArrival();
            if((thisStopArrival + DateTimeUtils.MIN_TRANSFER_SECONDS) > currentNodeTimeProperty) {
                return currentNode.getRelationships(Direction.OUTGOING, StopTimeNode.REL_NEXT_AWAITING_STOP);
            }

            //vytahnu si calendar pro tento trip a budu zjistovat, zda je trip platny v tomto dni
            final Relationship inTripRelationship = currentNode.getSingleRelationship(StopTimeNode.REL_IN_TRIP, Direction.OUTGOING);
            final Node currentTripNode = inTripRelationship.getEndNode();
            final String currentCalendarId = (String) currentTripNode.getProperty(TripNode.CALENDAR_ID_PROPERTY);
            final CalendarNode currentCalendarNode = calendarNodeMap.get(currentCalendarId);

            final LocalDateTime dateTimeToValidate = DateTimeUtils.getDateTimeToValidate(departureDateTime, currentStopIsOverMidnightInTrip, currentNodeTimeProperty, departureSecondsOfDay);
            //pokud neni trip v platnosti pro dany den, tak na nej nemuzu nastoupit a pokracuji jen pres next_awaiting_stop
            if(!DateTimeUtils.dateIsInCalendarValidity(currentCalendarNode, dateTimeToValidate)) {
                return currentNode.getRelationships(Direction.OUTGOING, StopTimeNode.REL_NEXT_AWAITING_STOP);
            }

        } else {
            //posledni hrana byla next_stop, mimo jine to znamena, ze currentNodeArrival nemuze byt null
            assert(currentNodeArrival != null);

            stopTripWrapper.setThisStopArrival(currentNodeArrival);
            //v ramci teto path jsem na teto stanici jiz byl (tedy se vracim, coz je nezadouci)
            //ovsem v ramci jednoho tripu muzu na jednu stanici vicenasobne
            if(visitedStops.containsKey(currentStopName)) {
                final Set<String> tripsWithThisStation = visitedStops.get(currentStopName);
                if(tripsWithThisStation.contains(currentTripId)) {
                    //na teto stanici jsem byl jiz na jinem tripu, nez aktualnim
                    return Iterables.empty();
                }
            } else {
                visitedStops.put(currentStopName, new HashSet<>());
            }

            visitedStops.get(currentStopName).add(currentTripId);

            //max pocet prestupu
            if(visitedTrips.size() > maxNumberOfTransfers) {
                //uz muzu jit jen po next_stop
                return currentNode.getRelationships(Direction.OUTGOING, StopTimeNode.REL_NEXT_STOP);
            }

            //pareto-optimalita
            if(this.visitedStops.containsKey(currentNodeStopTimeId)) {
                //a byl jsem na nem v pro me s priznivejsim casem vyjezdu
                if(this.visitedStops.get(currentNodeStopTimeId) < travelTimeWithPenalty) {
                    return Iterables.empty();
                }
            }

            //pokud to prislo sem, tak mam aktualne nejlepsi mozny
            this.visitedStops.put(currentNodeStopTimeId, travelTimeWithPenalty);

            int i = 0;
            RelationshipType prevRelationShipType = null;
            for(Relationship relationship : path.reverseRelationships()) {
                final boolean relationshipIsTypeNextAwaitingStop = relationship.isType(StopTimeNode.REL_NEXT_AWAITING_STOP);

                //sel jsem (N)-[NEXT_AWAITING_STOP]-(m)-[NEXT_STOP]-(o)
                if(prevRelationShipType != null && relationshipIsTypeNextAwaitingStop && prevRelationShipType.equals(StopTimeNode.REL_NEXT_STOP)) {
                    //kontrola unikatnosti tripu v ramci path
                    if(visitedTrips.contains(currentTripId)) {
                        return Iterables.empty();
                    } else {
                        visitedTrips.add(currentTripId);
                        if(visitedTrips.size() > maxNumberOfTransfers) {
                            return currentNode.getRelationships(Direction.OUTGOING, StopTimeNode.REL_NEXT_STOP);
                        }
                    }

                }

                if(relationshipIsTypeNextAwaitingStop) {
                    prevRelationShipType = StopTimeNode.REL_NEXT_AWAITING_STOP;
                } else {
                    prevRelationShipType = StopTimeNode.REL_NEXT_STOP;
                }

                if(++i >= 2) {
                    //iterovat chci jen maximalne pres 2 relace dozadu, vice nema smysl
                    break;
                }
            }
        }

        return currentNode.getRelationships(Direction.OUTGOING, StopTimeNode.REL_NEXT_STOP, StopTimeNode.REL_NEXT_AWAITING_STOP);
    }

    @Override
    public PathExpander<StopTripWrapper> reverse() {
        return this;
    }

}
