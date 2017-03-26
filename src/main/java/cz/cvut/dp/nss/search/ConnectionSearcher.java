package cz.cvut.dp.nss.search;

import cz.cvut.dp.nss.search.entity.calendar.CalendarNode;
import cz.cvut.dp.nss.search.entity.calendarDate.CalendarDateNode;
import cz.cvut.dp.nss.search.entity.stopTime.StopTimeNode;
import cz.cvut.dp.nss.search.entity.trip.TripNode;
import cz.cvut.dp.nss.search.utils.DateTimeUtils;
import cz.cvut.dp.nss.search.utils.comparator.SearchResultByDepartureDateComparator;
import cz.cvut.dp.nss.search.utils.traversal.DepartureBranchSelector;
import cz.cvut.dp.nss.search.utils.traversal.DepartureTypeEvaluator;
import cz.cvut.dp.nss.search.utils.traversal.DepartureTypeExpander;
import cz.cvut.dp.nss.search.utils.traversal.wrapper.StopTripWrapper;
import org.joda.time.LocalDateTime;
import org.neo4j.graphdb.*;
import org.neo4j.graphdb.traversal.InitialBranchState;
import org.neo4j.graphdb.traversal.TraversalDescription;
import org.neo4j.graphdb.traversal.Traverser;
import org.neo4j.graphdb.traversal.Uniqueness;
import org.neo4j.logging.Log;
import org.neo4j.procedure.Context;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.Procedure;

import java.util.*;
import java.util.stream.Stream;

import static org.neo4j.procedure.Mode.READ;

/**
 * @author jakubchalupa
 * @since 07.02.17
 */
public class ConnectionSearcher {

    // This field declares that we need a GraphDatabaseService
    // as context when any procedure in this class is invoked
    @Context
    public GraphDatabaseService db;

    // This gives us a log instance that outputs messages to the
    // standard log, `neo4j.log`
    @Context
    public Log log;

    /**
     * mapa se dny plastnosti (calendarId -> calendarNode i se dny vyjimek)
     * schvalne neni synchronized, zapis si sam synchronizuji pouze v initCalendarDates() a cteni preziji bez synchronized
     */
    private static final Map<String, CalendarNode> calendarNodeMap = new HashMap<>();

    /**
     * najde spojeni dle zadanych parametru
     * @param stopFromName stanize z
     * @param stopToName stanice d
     * @param departure cas odjezdu
     * @param maxDeparture maximalni cas prijezdu
     * @param maxTransfers maximalni pocet prestupu
     * @param withWheelChair true pokud chci jen kompletne bezbarierove vysledky (tedy nastupni, prestupni a vystupni stanice + jizdy)
     * @param stopToWheelChairAccessible pokud false, tak stopToName stanice (konecna) nemusi byt bezbarierova - je to pro ucely vyhledavani s prujezdnou stanici - hledani pres stanici, na ktere ale zustanu ve stejnem voze
     * @param stopTimeIdToSearchFrom id stopTime, ze ktereho zacnu vyhledavat. Pokud je poskytnuto, tak se bude vyhledavat pouze z tohoto!!!
     * @return stream nalezenych vysledku - serazene a vyfiltrovane
     */
    @Procedure(name = "cz.cvut.dp.nss.search.byDepartureSearch", mode = READ)
    public Stream<SearchResultWrapper> byDepartureSearch(@Name("stopFromName") String stopFromName, @Name("stopToName") String stopToName,
                                               @Name("departure") long departure, @Name("maxDeparture") long maxDeparture,
                                               @Name("maxTransfers") long maxTransfers, @Name("maxNumberOfResults") long maxNumberOfResults,
                                               @Name("wheelChair") boolean withWheelChair, @Name("stopToWheelChairAccessible") boolean stopToWheelChairAccessible,
                                               @Name("stopTimeIdToSearchFrom") Long stopTimeIdToSearchFrom) {

        //pokud mame prazdnou mapu calendarNodeMap tak ji inicializujeme
        if(calendarNodeMap.isEmpty()) {
            initCalendarDates();

            //a pokud je stale prazdna tak muzeme hned vratit prazdny vysledek hledani
            if(calendarNodeMap.isEmpty()) {
                return new ArrayList<SearchResultWrapper>().stream();
            }
        }

        //zjistim, jestli vubec existuje cilova stanice - pokud ne tak vracim prazdny vysledek hledani
        if(!stopWithNameExists(stopToName, stopToWheelChairAccessible)) {
            return new ArrayList<SearchResultWrapper>().stream();
        }

        final LocalDateTime departureDateTime = new LocalDateTime(departure);
        final int departureSecondsOfDay = departureDateTime.getMillisOfDay() / 1000;

        //najdu uzly ze kterych muzu vyrazit a jeste je zkontroluji na platnost calendar
        //vyhovujici pridavam do listu nodeList
        final ResourceIterator<Node> startNodes = findStartNodesForDepartureTypePathFinding(stopFromName, departure, maxDeparture, withWheelChair, stopTimeIdToSearchFrom);
        final List<Node> nodeList = new ArrayList<>();
        while(startNodes.hasNext()) {
            Node next = startNodes.next();

            final boolean overMidnightDepartureInTrip = (boolean) next.getProperty(StopTimeNode.OVER_MIDNIGHT_PROPERTY);
            //uzly zde urcite maji departure, protoze tak jsem je vyselectovat pomoci cypheru
            final long currentNodeDeparture = (long) next.getProperty(StopTimeNode.DEPARTURE_PROPERTY);

            //musim prejit na tripNode a z nej vzit calendarId
            final Relationship inTripRelationship = next.getSingleRelationship(StopTimeNode.REL_IN_TRIP, Direction.OUTGOING);
            final Node tripNode = inTripRelationship.getEndNode();
            final String calendarId = (String) tripNode.getProperty(TripNode.CALENDAR_ID_PROPERTY);

            final LocalDateTime dateTimeToValidate = DateTimeUtils.getDateTimeToValidate(departureDateTime, overMidnightDepartureInTrip, currentNodeDeparture, departureSecondsOfDay);
            if(DateTimeUtils.dateIsInCalendarValidity(calendarNodeMap.get(calendarId), dateTimeToValidate)) {
                nodeList.add(next);
            }
        }

        //predpis vyhledavani cest
        TraversalDescription traversalDescription = db.traversalDescription()
            .order((startBranch, expander) -> new DepartureBranchSelector(startBranch, expander, stopToName))
            .uniqueness(Uniqueness.NODE_PATH)
            .expand(new DepartureTypeExpander(departureDateTime, new LocalDateTime(maxDeparture),
                (int) maxTransfers, withWheelChair, calendarNodeMap), getEmptyInitialBranchState())
            .evaluator(new DepartureTypeEvaluator(stopToName, departureDateTime, (int) maxNumberOfResults, stopToWheelChairAccessible));

        Map<String, SearchResultWrapper> ridesMap = new HashMap<>();
        int secondsOfDepartureDay = departureDateTime.getMillisOfDay() / 1000;
        Traverser traverser = traversalDescription.traverse(nodeList);
        for(Path path : traverser) {
            final Node startNode = path.startNode();
            final Node endNode = path.endNode();

            final long secondsOfArrival = ((long) endNode.getProperty(StopTimeNode.ARRIVAL_PROPERTY));
            final long secondsOfDeparture = ((long) startNode.getProperty(StopTimeNode.DEPARTURE_PROPERTY));
            long travelTime;
            if(secondsOfArrival >= secondsOfDeparture) {
                travelTime = secondsOfArrival - secondsOfDeparture;
            } else {
                travelTime = (DateTimeUtils.SECONDS_IN_DAY - secondsOfDeparture) + secondsOfArrival;
            }

            boolean overMidnightDeparture = false;
            if(secondsOfDeparture < secondsOfDepartureDay) {
                overMidnightDeparture = true;
            }

            boolean overMidnightArrival = false;
            if(secondsOfArrival < secondsOfDepartureDay) {
                overMidnightArrival = true;
            }

            //vyberu tripy, po kterych jede cesta
            final Set<String> tripsOnPath = new LinkedHashSet<>();
            final List<Long> stopTimesOnPath = new ArrayList<>();
            RelationshipType prevRelationshipType = null;
            for(Relationship relationship : path.relationships()) {
                final Node relationshipStartNode = relationship.getStartNode();
                final long nodeStopTimeIdProperty = (long) relationshipStartNode.getProperty(StopTimeNode.STOP_TIME_ID_PROPERTY);
                final String nodeTripIdProperty = (String) relationshipStartNode.getProperty(StopTimeNode.TRIP_PROPERTY);

                if(prevRelationshipType != null && prevRelationshipType.equals(StopTimeNode.REL_NEXT_STOP) && relationship.isType(StopTimeNode.REL_NEXT_AWAITING_STOP)) {
                    stopTimesOnPath.add(nodeStopTimeIdProperty);
                }

                if(relationship.isType(StopTimeNode.REL_NEXT_STOP) && !tripsOnPath.contains(nodeTripIdProperty)) {
                    stopTimesOnPath.add(nodeStopTimeIdProperty);
                    tripsOnPath.add(nodeTripIdProperty);
                }

                if(relationship.isType(StopTimeNode.REL_NEXT_AWAITING_STOP)) {
                    prevRelationshipType = StopTimeNode.REL_NEXT_AWAITING_STOP;
                } else {
                    prevRelationshipType = StopTimeNode.REL_NEXT_STOP;
                }
            }

            final long lastStopTimeId = (long) endNode.getProperty(StopTimeNode.STOP_TIME_ID_PROPERTY);

            if(stopTimesOnPath.get(stopTimesOnPath.size() - 1) != lastStopTimeId) {
                stopTimesOnPath.add(lastStopTimeId);
            }

            StringBuilder stringBuilder = new StringBuilder();
            for(String tripId : tripsOnPath) {
                if(stringBuilder.length() != 0) {
                    stringBuilder.append("-");
                }
                stringBuilder.append(tripId);
            }

            String pathIdentifier = stringBuilder.toString();
            if(!ridesMap.containsKey(pathIdentifier) || travelTime < ridesMap.get(pathIdentifier).getTravelTime()) {
                SearchResultWrapper wrapper = new SearchResultWrapper();
                wrapper.setDeparture(secondsOfDeparture);
                wrapper.setArrival(secondsOfArrival);
                wrapper.setTravelTime(travelTime);
                wrapper.setOverMidnightDeparture(overMidnightDeparture);
                wrapper.setOverMidnightArrival(overMidnightArrival);
                wrapper.setStops(stopTimesOnPath);
                wrapper.setNumberOfTransfers(tripsOnPath.size() - 1);
                ridesMap.put(pathIdentifier, wrapper);
            }

        }

        //vysledky vyhledavani dam do listu a vratim. momentalne tam jsou vysledky, ktere dale musi byt vyfiltrovany!
        List<SearchResultWrapper> searchResultWrappers = transformSearchResultWrapperMapToList(ridesMap);
        List<SearchResultWrapper> toRet = sortAndFilterSearchResults(searchResultWrappers, (int) maxNumberOfResults);

        return toRet.stream();
    }

    @Procedure(name = "cz.cvut.dp.nss.search.initCalendarDates", mode = READ)
    public void initCalendarDates() {
        Map<String, CalendarNode> map = new HashMap<>();
        ResourceIterator<Node> calendarNodes = db.findNodes(CalendarNode.NODE_LABEL);
        while(calendarNodes.hasNext()) {
            Node calendarNode = calendarNodes.next();
            CalendarNode realCalendarNode = getCalendarNodeFromNode(calendarNode);

            Iterable<Relationship> relationships = calendarNode.getRelationships(CalendarNode.REL_IN_CALENDAR, Direction.INCOMING);
            Set<CalendarDateNode> calendarDateNodes = new HashSet<>();
            for(Relationship relationship : relationships) {
                calendarDateNodes.add(getCalendarDateNodeFromNode(relationship.getStartNode()));
            }
            realCalendarNode.setCalendarDateNodes(calendarDateNodes);

            map.put(realCalendarNode.getCalendarId(), realCalendarNode);
        }

        synchronized(calendarNodeMap) {
            calendarNodeMap.clear();
            calendarNodeMap.putAll(map);
        }
    }

    /**
     * @param stopName nazev stanice
     * @param wheelChairAccessible pokud true, hledam navic jen bezbarierove stanice
     * @return true, pokud v db je alespon jeden stopTimeNode na dane stanici
     */
    private boolean stopWithNameExists(String stopName, boolean wheelChairAccessible) {
        Map<String, Object> params = new HashMap<>();
        params.put("stopName", stopName);

        String queryString = "match (s:StopTimeNode {stopName: {stopName}}) ";
        if(wheelChairAccessible) queryString += "where s.wheelChair ";
        queryString += "return s limit 1";
        Result result = db.execute(queryString, params);
        return result.hasNext();
    }

    /**
     * @param searchResultWrappers vysledky hledani k serazeni a filtrovani
     * @return serazene a vyfiltrovane vysledky hledani
     */
    private static List<SearchResultWrapper> sortAndFilterSearchResults(List<SearchResultWrapper> searchResultWrappers, int maxNumberOfResults) {
        //seradim vysledky vlastnim algoritmem
        searchResultWrappers.sort(new SearchResultByDepartureDateComparator());

        if(searchResultWrappers.size() <= maxNumberOfResults) {
            return searchResultWrappers;
        }

        List<SearchResultWrapper> retList = new ArrayList<>(maxNumberOfResults);
        for(int i = 0; i < maxNumberOfResults; i++) {
            retList.add(i, searchResultWrappers.get(i));
        }

        return retList;
    }

    /**
     * najde vychozi stopy pro traverzovani (hledani dle departureInMillis), ale bez osetreni na platnost calendar!
     * @param stopFromName id vychozi stanice
     * @param departureInMillis datum odjezdu
     * @param maxDateDepartureInMillis max datum odjezdu
     * @param withWheelChair pokud true tak hledam jen vychozi stopy, ktere jsou bezbarierove
     * @param stopTimeIdToSearchFrom id stopTimu, ze ktereho zacnu vyhledavat. Pokud je uvedeno tak se vyhledava pouze z tohoto!!!
     * @return vychozi zastaveni (serazena)
     */
    private ResourceIterator<Node> findStartNodesForDepartureTypePathFinding(String stopFromName, long departureInMillis,
                                                                             long maxDateDepartureInMillis, boolean withWheelChair, Long stopTimeIdToSearchFrom) {
        LocalDateTime tempDateDeparture = new LocalDateTime(departureInMillis);
        LocalDateTime tempMaxDateDeparture = new LocalDateTime(maxDateDepartureInMillis);

        Map<String, Object> params = new HashMap<>();
        params.put("stopFromName", stopFromName);
        params.put("departureTimeInSeconds", tempDateDeparture.getMillisOfDay() / 1000);
        params.put("maxDepartureTimeInSeconds", tempMaxDateDeparture.getMillisOfDay() / 1000);

        String queryString = "match (s:StopTimeNode {stopName: {stopFromName}})-[IN_TRIP]->(t:TripNode) where s.departureInSeconds is not null ";
        if(stopTimeIdToSearchFrom == null) {
            if(tempMaxDateDeparture.getMillisOfDay() > tempDateDeparture.getMillisOfDay()) {
                //neprehoupl jsem se pres pulnoc
                queryString += "and s.departureInSeconds >= {departureTimeInSeconds} and s.departureInSeconds < {maxDepartureTimeInSeconds} ";
            } else {
                //prehoupl jsem se s rozsahem pres pulnoc
                queryString += "and ((s.departureInSeconds >= {departureTimeInSeconds}) or (s.departureInSeconds < {maxDepartureTimeInSeconds})) ";
            }

            if(withWheelChair) {
                queryString += "and s.wheelChair ";
            }
        } else {
            //hledam z jednoho konkretniho stopTimu, ani nemusim resit vozicek, protoze to je pouze prujezdni
            params.put("stopTimeId", stopTimeIdToSearchFrom);
            queryString += "and s.stopTimeId = {stopTimeId} ";
        }

        queryString += "return s ";
        queryString += "order by case when s.departureInSeconds < {departureTimeInSeconds} then 2 else 1 end, s.departureInSeconds asc";

        Result result = db.execute(queryString, params);
        return result.columnAs("s");
    }

    /**
     * vrati list value hodnot z mapy
     * @param ridesMap mapa search result wrapperu
     * @return list search result wrapperu
     */
    private List<SearchResultWrapper> transformSearchResultWrapperMapToList(Map<String, SearchResultWrapper> ridesMap) {
        List<SearchResultWrapper> resultList = new ArrayList<>();
        for(Map.Entry<String, SearchResultWrapper> entry : ridesMap.entrySet()) {
            resultList.add(entry.getValue());
        }

        return resultList;
    }

    private static CalendarNode getCalendarNodeFromNode(Node node) {
        CalendarNode calendarNode = new CalendarNode();
        calendarNode.setCalendarId((String) node.getProperty(CalendarNode.CALENDAR_ID_PROPERTY));
        calendarNode.setFromDateInSeconds((long) node.getProperty(CalendarNode.FROM_DATE_PROPERTY));
        calendarNode.setToDateInSeconds((long) node.getProperty(CalendarNode.TO_DATE_PROPERTY));
        calendarNode.setMonday((boolean) node.getProperty(CalendarNode.MONDAY));
        calendarNode.setTuesday((boolean) node.getProperty(CalendarNode.TUESDAY));
        calendarNode.setWednesday((boolean) node.getProperty(CalendarNode.WEDNESDAY));
        calendarNode.setThursday((boolean) node.getProperty(CalendarNode.THURSDAY));
        calendarNode.setFriday((boolean) node.getProperty(CalendarNode.FRIDAY));
        calendarNode.setSaturday((boolean) node.getProperty(CalendarNode.SATURDAY));
        calendarNode.setSunday((boolean) node.getProperty(CalendarNode.SUNDAY));

        return calendarNode;
    }

    private static CalendarDateNode getCalendarDateNodeFromNode(Node node) {
        CalendarDateNode calendarDateNode = new CalendarDateNode();
        calendarDateNode.setCalendarDateId((long) node.getProperty(CalendarDateNode.CALENDAR_DATE_ID_PROPERTY));
        calendarDateNode.setDateInSeconds((long) node.getProperty(CalendarDateNode.CALENDAR_DATE_IN_SECONDS_PROPERTY));
        calendarDateNode.setInclude((boolean) node.getProperty(CalendarDateNode.CALENDAR_DATE_INCLUDE_PROPERTY));

        return calendarDateNode;
    }

    /**
     * @return initial branch state for neo4j traversing.
     */
    private InitialBranchState<StopTripWrapper> getEmptyInitialBranchState() {

        return new InitialBranchState<StopTripWrapper>() {

            @Override
            public InitialBranchState<StopTripWrapper> reverse() {
                return this;
            }

            @Override
            public StopTripWrapper initialState(Path path) {
                return new StopTripWrapper();
            }

        };
    }

}
