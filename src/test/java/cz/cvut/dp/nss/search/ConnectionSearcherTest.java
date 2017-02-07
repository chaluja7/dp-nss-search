package cz.cvut.dp.nss.search;

import org.junit.Test;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;

import java.io.File;
import java.util.Map;

/**
 * @author jakubchalupa
 * @since 07.02.17
 */
public class ConnectionSearcherTest {

    // This rule starts a Neo4j instance for us
    //@Rule
    //public Neo4jRule neo4j = new Neo4jRule().withProcedure(ConnectionSearcher.class);

    private final GraphDatabaseService db = new GraphDatabaseFactory().newEmbeddedDatabase(new File("/Users/jakubchalupa/Documents/Neo4j/default.graphdb"));

    @Test
    public void testByDepartureSearch() throws Throwable {

        Result result = db.execute("CALL cz.cvut.dp.nss.search.byDepartureSearch('BrownA_T01')");

        int i = 0;
        while(result.hasNext()) {
            Map<String, Object> next = result.next();
            i++;
        }

        int k = 0;


//
//        // In a try-block, to make sure we close the driver after the test
//        try(Driver driver = GraphDatabase.driver(neo4j.boltURI() , Config.build().withEncryptionLevel(Config.EncryptionLevel.NONE).toConfig()))
//        {
//
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
//
//
//        }
    }

}
