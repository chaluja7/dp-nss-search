package cz.cvut.dp.nss.search;

import cz.cvut.dp.nss.search.entity.stopTime.StopTimeNode;
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
        DateTime departureDateTime = formatter.parseDateTime("01.02.2017 18:54");
        DateTime maxDepartureDateTime = formatter.parseDateTime("01.02.2017 21:54");

        long departureMillis = departureDateTime.getMillis();
        long maxDepartureMillis = maxDepartureDateTime.getMillis();

        Result result = db.execute("CALL cz.cvut.dp.nss.search.byDepartureSearch('Dejvická', 'Nové Butovice', " + departureMillis + ", " + maxDepartureMillis + ", 1)");

        List<Map<String, Object>> list = new ArrayList<>();
        int i = 0;


        while(result.hasNext()) {
            Map<String, Object> next = result.next();
            list.add(next);

            List<Long> stops = (List<Long>) next.get("stops");
            List<String> actualStops = new ArrayList<>();
            db.beginTx();
            for(Long stopTimeId : stops) {
                actualStops.add((String) db.findNode(StopTimeNode.NODE_LABEL, "stopTimeId", stopTimeId).getProperty("stopName"));
            }

            next.put("actualStops", actualStops);
            i++;
        }


        int k = 0;


//        // In a try-block, to make sure we close the driver after the test
//        try(Driver driver = GraphDatabase.driver(neo4j.boltURI() , Config.build().withEncryptionLevel(Config.EncryptionLevel.NONE).toConfig()))
//        {
//            // Given I've started Neo4j with the FullTextIndex procedure class
//            //       which my 'neo4j' rule above does.
//            Session session = driver.session();
//
//            // Then I can search for that node with lucene query syntax
//            StatementResult result = session.run( "CALL cz.cvut.dp.nss.search.byDepartureSearch('BrownA_T01')" );
//
//
//            while(result.hasNext()) {
//                Record next = result.next();
//                int i = 0;
//            }
//        }
    }

    @Test
    public void testInitCalendarDates() throws Throwable {
        db.execute("CALL cz.cvut.dp.nss.search.initCalendarDates()");
    }

}
