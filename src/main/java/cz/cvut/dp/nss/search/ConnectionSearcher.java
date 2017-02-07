package cz.cvut.dp.nss.search;

import org.neo4j.graphdb.*;
import org.neo4j.graphdb.traversal.Evaluators;
import org.neo4j.graphdb.traversal.TraversalDescription;
import org.neo4j.graphdb.traversal.Traverser;
import org.neo4j.logging.Log;
import org.neo4j.procedure.Context;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.Procedure;

import java.util.ArrayList;
import java.util.List;
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

    @Procedure(name = "cz.cvut.dp.nss.search.byDepartureSearch", mode = READ)
    public Stream<SearchHit> byDepartureSearch(@Name("tripId") String tripId) {

        Node node = db.findNode(Label.label("TripNode"), "tripId", tripId);

        TraversalDescription td = db.traversalDescription()
            .breadthFirst()
            .relationships(RelationshipType.withName("IN_TRIP"), Direction.INCOMING)
            .evaluator(Evaluators.includeWhereLastRelationshipTypeIs(RelationshipType.withName("IN_TRIP")));


        List<SearchHit> list = new ArrayList<>();
        if(node != null) {
            Traverser traverser = td.traverse(node);
            for (Path path : traverser) {
                list.add(new SearchHit(path.startNode(), path.endNode()));
            }
        }

        return list.stream();
    }

    public static class SearchHit {

        public long startNodeId;

        public long endNodeId;

        SearchHit(Node startNode, Node endNode) {
            this.startNodeId = startNode.getId();
            this.endNodeId = endNode.getId();
        }

    }


}
