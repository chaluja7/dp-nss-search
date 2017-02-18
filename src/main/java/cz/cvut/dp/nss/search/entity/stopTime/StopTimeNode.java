package cz.cvut.dp.nss.search.entity.stopTime;

import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.RelationshipType;

/**
 * @author jakubchalupa
 * @since 11.02.17
 */
public class StopTimeNode {

    public static final String STOP_TIME_ID_PROPERTY = "stopTimeId";

    public static final String STOP_ID_PROPERTY = "stopId";

    public static final String STOP_NAME_PROPERTY = "stopName";

    public static final String TRIP_PROPERTY = "tripId";

    public static final String DEPARTURE_PROPERTY = "departureInSeconds";

    public static final String ARRIVAL_PROPERTY = "arrivalInSeconds";

    public static final String OVER_MIDNIGHT_PROPERTY = "overMidnightDepartureInTrip";

    public static final Label NODE_LABEL = () -> "StopTimeNode";

    public static final RelationshipType REL_NEXT_STOP = () -> "NEXT_STOP";

    public static final RelationshipType REL_NEXT_AWAITING_STOP = () -> "NEXT_AWAITING_STOP";

    public static final RelationshipType REL_IN_TRIP = () -> "IN_TRIP";

}
