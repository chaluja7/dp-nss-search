package cz.cvut.dp.nss.search;

import cz.cvut.dp.nss.search.utils.DateTimeUtils;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
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

    // This rule starts a Neo4j instance for us
    //@Rule
    //public Neo4jRule neo4j = new Neo4jRule().withProcedure(ConnectionSearcher.class);

    private final GraphDatabaseService db = new GraphDatabaseFactory().
        newEmbeddedDatabase(new File("/Users/jakubchalupa/Documents/FEL/MGR/DP/neo4j/neo4jPidTesting/data/databases/graph.db"));

    @Test
    public void testByDepartureSearch() throws Throwable {
        db.execute("CALL cz.cvut.dp.nss.search.initCalendarDates()");

        DateTimeFormatter formatter = DateTimeFormat.forPattern(DateTimeUtils.DATE_TIME_PATTERN);
        DateTime departureDateTime = formatter.parseDateTime("02.02.2017 9:00");
        DateTime maxDepartureDateTime = formatter.parseDateTime("02.02.2017 11:00");

        long departureMillis = departureDateTime.getMillis();
        long maxDepartureMillis = maxDepartureDateTime.getMillis();

        Result result = db.execute("CALL cz.cvut.dp.nss.search.byDepartureSearch('Červeňanského', 'Na Strži', " + departureMillis + ", " + maxDepartureMillis + ", 2)");

        List<Map<String, Object>> list = new ArrayList<>();
        while(result.hasNext()) {
            list.add(result.next());
        }


        int k = 0;
    }

    @Test
    public void testInitCalendarDates() throws Throwable {
        db.execute("CALL cz.cvut.dp.nss.search.initCalendarDates()");
    }

}
