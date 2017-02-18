package cz.cvut.dp.nss.search.entity.calendar;


import cz.cvut.dp.nss.search.entity.calendarDate.CalendarDateNode;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.RelationshipType;

import java.util.Set;

/**
 * CREATE CONSTRAINT ON (n:CalendarNode) ASSERT n.calendarId IS UNIQUE
 *
 * @author jakubchalupa
 * @since 11.02.17
 */
public class CalendarNode {

    public static final String CALENDAR_ID_PROPERTY = "calendarId";

    public static final String FROM_DATE_PROPERTY = "fromDateInSeconds";

    public static final String TO_DATE_PROPERTY = "toDateInSeconds";

    public static final String MONDAY = "monday";

    public static final String TUESDAY = "tuesday";

    public static final String WEDNESDAY = "wednesday";

    public static final String THURSDAY = "thursday";

    public static final String FRIDAY = "friday";

    public static final String SATURDAY = "saturday";

    public static final String SUNDAY = "sunday";

    public static final Label NODE_LABEL = () -> "CalendarNode";

    public static final RelationshipType REL_IN_CALENDAR = () -> "IN_CALENDAR";

    private String calendarId;

    private boolean monday;

    private boolean tuesday;

    private boolean wednesday;

    private boolean thursday;

    private boolean friday;

    private boolean saturday;

    private boolean sunday;

    private long fromDateInSeconds;

    private long toDateInSeconds;

    /**
     * navazana dny vyjimek
     */
    private Set<CalendarDateNode> calendarDateNodes;

    public String getCalendarId() {
        return calendarId;
    }

    public void setCalendarId(String calendarId) {
        this.calendarId = calendarId;
    }

    public boolean isMonday() {
        return monday;
    }

    public void setMonday(boolean monday) {
        this.monday = monday;
    }

    public boolean isTuesday() {
        return tuesday;
    }

    public void setTuesday(boolean tuesday) {
        this.tuesday = tuesday;
    }

    public boolean isWednesday() {
        return wednesday;
    }

    public void setWednesday(boolean wednesday) {
        this.wednesday = wednesday;
    }

    public boolean isThursday() {
        return thursday;
    }

    public void setThursday(boolean thursday) {
        this.thursday = thursday;
    }

    public boolean isFriday() {
        return friday;
    }

    public void setFriday(boolean friday) {
        this.friday = friday;
    }

    public boolean isSaturday() {
        return saturday;
    }

    public void setSaturday(boolean saturday) {
        this.saturday = saturday;
    }

    public boolean isSunday() {
        return sunday;
    }

    public void setSunday(boolean sunday) {
        this.sunday = sunday;
    }

    public long getFromDateInSeconds() {
        return fromDateInSeconds;
    }

    public void setFromDateInSeconds(long fromDateInSeconds) {
        this.fromDateInSeconds = fromDateInSeconds;
    }

    public long getToDateInSeconds() {
        return toDateInSeconds;
    }

    public void setToDateInSeconds(long toDateInSeconds) {
        this.toDateInSeconds = toDateInSeconds;
    }

    public Set<CalendarDateNode> getCalendarDateNodes() {
        return calendarDateNodes;
    }

    public void setCalendarDateNodes(Set<CalendarDateNode> calendarDateNodes) {
        this.calendarDateNodes = calendarDateNodes;
    }

    public boolean isValidInDayOfWeek(int dayOfWeek) {
        switch(dayOfWeek) {
            case 1:
                return isMonday();
            case 2:
                return isTuesday();
            case 3:
                return isWednesday();
            case 4:
                return isThursday();
            case 5:
                return isFriday();
            case 6:
                return isSaturday();
            case 7:
                return isSunday();
            default:
            throw new RuntimeException("day of week must be in range 1 - 7");
        }
    }
}
