package cz.cvut.dp.nss.search;

import cz.cvut.dp.nss.search.utils.DateTimeUtils;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.junit.Ignore;
import org.junit.Test;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author jakubchalupa
 * @since 07.02.17
 */
public class ConnectionSearcherTest {

    private final GraphDatabaseService db = new GraphDatabaseFactory().
        newEmbeddedDatabase(new File("/Users/jakubchalupa/Documents/FEL/MGR/DP/neo4j/neo4jPid/data/databases/graph.db"));

    @Test
    @Ignore
    public void testByDepartureSearch() throws Throwable {
        DateTimeFormatter formatter = DateTimeFormat.forPattern(DateTimeUtils.DATE_TIME_PATTERN);
        DateTime departureDateTime = formatter.parseDateTime("02.02.2017 9:00");
        DateTime maxDepartureDateTime = formatter.parseDateTime("02.02.2017 11:00");

        long departureMillis = departureDateTime.getMillis();
        long maxDepartureMillis = maxDepartureDateTime.getMillis();

        Result result = db.execute("CALL cz.cvut.dp.nss.search.byDepartureSearch('Dejvická', 'Karlovo náměstí', " + departureMillis + ", " + maxDepartureMillis + ", 2, 3, false, false, 2563)");

        List<Map<String, Object>> list = new ArrayList<>();
        while(result.hasNext()) {
            list.add(result.next());
        }
    }

    @Test
    @Ignore
    public void testByArrivalSearch() throws Throwable {
        DateTimeFormatter formatter = DateTimeFormat.forPattern(DateTimeUtils.DATE_TIME_PATTERN);
        DateTime arrivalDateTime = formatter.parseDateTime("03.05.2017 15:00");
        DateTime minArrivalDateTime = formatter.parseDateTime("03.05.2017 09:00");

        long arrivalMillis = arrivalDateTime.getMillis();
        long minArrivalMillis = minArrivalDateTime.getMillis();

        Result result = db.execute("CALL cz.cvut.dp.nss.search.byArrivalSearch('Dejvická', 'Karlovo náměstí', " + arrivalMillis + ", " + minArrivalMillis + ", 3, 3, false, false, null)");

        List<Map<String, Object>> list = new ArrayList<>();
        while(result.hasNext()) {
            list.add(result.next());
        }
    }

    @Test
    @Ignore
    public void testInitCalendarDates() throws Throwable {
        db.execute("CALL cz.cvut.dp.nss.search.initCalendarDates()");
    }

}
