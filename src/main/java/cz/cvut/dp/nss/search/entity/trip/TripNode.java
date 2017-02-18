package cz.cvut.dp.nss.search.entity.trip;

import org.neo4j.graphdb.RelationshipType;

/**
 * @author jakubchalupa
 * @since 11.02.17
 */
public class TripNode {

    public static final String CALENDAR_ID_PROPERTY = "calendarId";

    public static final RelationshipType REL_IN_TRIP = () -> "IN_TRIP";

}
