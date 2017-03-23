package cz.cvut.dp.nss.search;

import cz.cvut.dp.nss.search.entity.calendar.CalendarNode;
import cz.cvut.dp.nss.search.entity.calendarDate.CalendarDateNode;
import cz.cvut.dp.nss.search.entity.stopTime.StopTimeNode;
import cz.cvut.dp.nss.search.entity.trip.TripNode;
import cz.cvut.dp.nss.search.utils.DateTimeUtils;
import cz.cvut.dp.nss.search.utils.comparator.SearchResultByDepartureDateComparator;
import cz.cvut.dp.nss.search.utils.filter.SearchResultFilter;
import cz.cvut.dp.nss.search.utils.traversal.CustomBranchOrderingPolicies;
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
     * max pocet vysledku vyhledavani
     */
    private static final int MAX_NUMBER_OF_RESULTS = 3;

    @Procedure(name = "cz.cvut.dp.nss.search.byDepartureSearch", mode = READ)
    public Stream<SearchResultWrapper> byDepartureSearch(@Name("stopFromName") String stopFromName, @Name("stopToName") String stopToName,
                                               @Name("departure") long departure, @Name("maxDeparture") long maxDeparture,
                                               @Name("maxTransfers") long maxTransfers) {

        //pokud mame prazdnou mapu calendarNodeMap tak ji inicializujeme
        if(calendarNodeMap.isEmpty()) {
            initCalendarDates();

            //a pokud je stale prazdna tak muzeme hned vratit prazdny vysledek hledani
            if(calendarNodeMap.isEmpty()) {
                return new ArrayList<SearchResultWrapper>().stream();
            }
        }

        //zjistim, jestli vubec existuje cilova stanice - pokud ne tak vracim prazdny vysledek hledani
        if(!stopWithNameExists(stopToName)) {
            return new ArrayList<SearchResultWrapper>().stream();
        }

        final LocalDateTime departureDateTime = new LocalDateTime(departure);
        final int departureSecondsOfDay = departureDateTime.getMillisOfDay() / 1000;

        //najdu uzly ze kterych muzu vyrazit a jeste je zkontroluji na platnost calendar
        //vyhovujici pridavam do listu nodeList
        final ResourceIterator<Node> startNodes = findStartNodesForDepartureTypePathFinding(stopFromName, departure, maxDeparture);
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
            .order(CustomBranchOrderingPolicies.DEPARTURE_ORDERING)
            .uniqueness(Uniqueness.NODE_PATH)
            .expand(new DepartureTypeExpander(departureDateTime, new LocalDateTime(maxDeparture),
                (int) maxTransfers, calendarNodeMap), getEmptyInitialBranchState())
            .evaluator(new DepartureTypeEvaluator(stopToName, departureDateTime, MAX_NUMBER_OF_RESULTS));

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
            final List<String> stopTimesOnPathInfo = new ArrayList<>();
            RelationshipType prevRelationshipType = null;
            for(Relationship relationship : path.relationships()) {
                final Node relationshipStartNode = relationship.getStartNode();
                final long nodeStopTimeIdProperty = (long) relationshipStartNode.getProperty(StopTimeNode.STOP_TIME_ID_PROPERTY);
                final String nodeTripIdProperty = (String) relationshipStartNode.getProperty(StopTimeNode.TRIP_PROPERTY);

                final String nodeStopNameProperty = (String) relationshipStartNode.getProperty(StopTimeNode.STOP_NAME_PROPERTY);
                final Long nodeArrivalProperty = (Long) relationshipStartNode.getProperty(StopTimeNode.ARRIVAL_PROPERTY);
                final Long nodeDepartureProperty = (Long) relationshipStartNode.getProperty(StopTimeNode.DEPARTURE_PROPERTY);


                if(prevRelationshipType != null && prevRelationshipType.equals(StopTimeNode.REL_NEXT_STOP) && relationship.isType(StopTimeNode.REL_NEXT_AWAITING_STOP)) {
                    stopTimesOnPath.add(nodeStopTimeIdProperty);
                    stopTimesOnPathInfo.add(getStopTimeInfoTmp(nodeStopNameProperty, nodeTripIdProperty, nodeArrivalProperty, nodeDepartureProperty));
                }

                if(relationship.isType(StopTimeNode.REL_NEXT_STOP) && !tripsOnPath.contains(nodeTripIdProperty)) {
                    stopTimesOnPath.add(nodeStopTimeIdProperty);
                    stopTimesOnPathInfo.add(getStopTimeInfoTmp(nodeStopNameProperty, nodeTripIdProperty, nodeArrivalProperty, nodeDepartureProperty));
                    tripsOnPath.add(nodeTripIdProperty);
                }

                if(relationship.isType(StopTimeNode.REL_NEXT_AWAITING_STOP)) {
                    prevRelationshipType = StopTimeNode.REL_NEXT_AWAITING_STOP;
                } else {
                    prevRelationshipType = StopTimeNode.REL_NEXT_STOP;
                }
            }

            final long lastStopTimeId = (long) endNode.getProperty(StopTimeNode.STOP_TIME_ID_PROPERTY);

            final String endNodeStopNameProperty = (String) endNode.getProperty(StopTimeNode.STOP_NAME_PROPERTY);
            final String endNodeTripProperty = (String) endNode.getProperty(StopTimeNode.TRIP_PROPERTY);
            final Long endNodeArrivalProperty = (Long) endNode.getProperty(StopTimeNode.ARRIVAL_PROPERTY);
            final Long endNodeDepartureProperty = (Long) endNode.getProperty(StopTimeNode.DEPARTURE_PROPERTY);
            if(stopTimesOnPath.get(stopTimesOnPath.size() - 1) != lastStopTimeId) {
                stopTimesOnPath.add(lastStopTimeId);
                stopTimesOnPathInfo.add(getStopTimeInfoTmp(endNodeStopNameProperty, endNodeTripProperty, endNodeArrivalProperty, endNodeDepartureProperty));
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
                //TODO docasne jen pro debug
                wrapper.setStopDetails(stopTimesOnPathInfo);
                wrapper.setNumberOfTransfers(tripsOnPath.size() - 1);
                ridesMap.put(pathIdentifier, wrapper);
            }

        }

        //vysledky vyhledavani dam do listu a vratim. momentalne tam jsou vysledky, ktere dale musi byt vyfiltrovany!
        List<SearchResultWrapper> searchResultWrappers = transformSearchResultWrapperMapToList(ridesMap);
        List<SearchResultWrapper> toRet = sortAndFilterSearchResults(searchResultWrappers);

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
     * @return true, pokud v db je alespon jeden stopTimeNode na dane stanici
     */
    private boolean stopWithNameExists(String stopName) {
        Map<String, Object> params = new HashMap<>();
        params.put("stopName", stopName);

        String queryString = "match (s:StopTimeNode {stopName: {stopName}}) return s limit 1";
        Result result = db.execute(queryString, params);
        return result.hasNext();
    }

    /**
     * @param searchResultWrappers vysledky hledani k serazeni a filtrovani
     * @return serazene a vyfiltrovane vysledky hledani
     */
    private static List<SearchResultWrapper> sortAndFilterSearchResults(List<SearchResultWrapper> searchResultWrappers) {
        //seradim vysledky vlastnim algoritmem
        searchResultWrappers.sort(new SearchResultByDepartureDateComparator());

        //vratim jen ty nejrelevantnejsi vyfiltrovane vysledky
        List<SearchResultWrapper> filteredList = SearchResultFilter.getFilteredResults(searchResultWrappers);

        if(filteredList.size() <= MAX_NUMBER_OF_RESULTS) {
            return filteredList;
        }

        List<SearchResultWrapper> retList = new ArrayList<>(MAX_NUMBER_OF_RESULTS);
        for(int i = 0; i < MAX_NUMBER_OF_RESULTS; i++) {
            retList.add(i, filteredList.get(i));
        }

        return retList;
    }

    private static String getStopTimeInfoTmp(String stopName, String tripId, Long arrival, Long departure) {
        arrival = arrival != null ? arrival * 1000 : 0L;
        departure = departure != null ? departure * 1000 : 0L;


        LocalDateTime arrivalDateTime = new LocalDateTime().withMillisOfDay(arrival.intValue());
        LocalDateTime departureDateTime = new LocalDateTime().withMillisOfDay(departure.intValue());


        StringBuilder builder = new StringBuilder();
        builder.append("Stanice: ").append(stopName).append("; trip ID: ").append(tripId);
        builder.append(" Příjezd: ").append(arrivalDateTime).append("; Odjezd: ").append(departureDateTime);

        return builder.toString();
    }

    /**
     * najde vychozi stopy pro traverzovani (hledani dle departureInMillis), ale bez osetreni na platnost calendar!
     * @param stopFromName id vychozi stanice
     * @param departureInMillis datum odjezdu
     * @param maxDateDepartureInMillis max datum odjezdu
     * @return vychozi zastaveni (serazena)
     */
    protected ResourceIterator<Node> findStartNodesForDepartureTypePathFinding(String stopFromName, long departureInMillis, long maxDateDepartureInMillis) {
        LocalDateTime tempDateDeparture = new LocalDateTime(departureInMillis);
        LocalDateTime tempMaxDateDeparture = new LocalDateTime(maxDateDepartureInMillis);

        Map<String, Object> params = new HashMap<>();
        params.put("stopFromName", stopFromName);
        params.put("departureTimeInSeconds", tempDateDeparture.getMillisOfDay() / 1000);
        params.put("maxDepartureTimeInSeconds", tempMaxDateDeparture.getMillisOfDay() / 1000);

        String queryString = "match (s:StopTimeNode {stopName: {stopFromName}})-[IN_TRIP]->(t:TripNode) where s.departureInSeconds is not null ";
        if(tempMaxDateDeparture.getMillisOfDay() > tempDateDeparture.getMillisOfDay()) {
            //neprehoupl jsem se pres pulnoc
            queryString += "and s.departureInSeconds >= {departureTimeInSeconds} and s.departureInSeconds < {maxDepartureTimeInSeconds} ";
        } else {
            //prehoupl jsem se s rozsahem pres pulnoc
            queryString += "and ((s.departureInSeconds >= {departureTimeInSeconds}) or (s.departureInSeconds < {maxDepartureTimeInSeconds})) ";
        }

        //pokud pouziju tohle, tak to pak neumi neo4j preves na Node
//        queryString += "return s {.*, calendarId: t.calendarId} ";
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
