package cz.cvut.dp.nss.search.entity.calendarDate;

/**
 * @author jakubchalupa
 * @since 11.02.17
 */
public class CalendarDateNode {

    public static final String CALENDAR_DATE_ID_PROPERTY = "calendarDateId";

    public static final String CALENDAR_DATE_IN_SECONDS_PROPERTY = "dateInSeconds";

    public static final String CALENDAR_DATE_INCLUDE_PROPERTY = "include";

    private long calendarDateId;

    /**
     * presny cas pulnoci prislusneho dne ve vterinach since epoch
     */
    private long dateInSeconds;

    /**
     * true = include, false = exclude
     */
    private boolean include;

    public long getCalendarDateId() {
        return calendarDateId;
    }

    public void setCalendarDateId(long calendarDateId) {
        this.calendarDateId = calendarDateId;
    }

    public long getDateInSeconds() {
        return dateInSeconds;
    }

    public void setDateInSeconds(long dateInSeconds) {
        this.dateInSeconds = dateInSeconds;
    }

    public boolean isInclude() {
        return include;
    }

    public void setInclude(boolean include) {
        this.include = include;
    }
}
