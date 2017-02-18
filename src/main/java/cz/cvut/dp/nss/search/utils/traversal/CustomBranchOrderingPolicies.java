package cz.cvut.dp.nss.search.utils.traversal;

import org.neo4j.graphdb.PathExpander;
import org.neo4j.graphdb.traversal.BranchOrderingPolicy;
import org.neo4j.graphdb.traversal.BranchSelector;
import org.neo4j.graphdb.traversal.TraversalBranch;

/**
 * Branch ordering policies for Neo4j traversal.
 *
 * @author jakubchalupa
 * @since 23.04.15
 */
public enum CustomBranchOrderingPolicies implements BranchOrderingPolicy {

    DEPARTURE_ORDERING {
        public BranchSelector create(TraversalBranch startBranch, PathExpander expander) {
            return new DepartureBranchSelector(startBranch, expander);
        }
    },
//    ARRIVAL_ORDERING {
//        public BranchSelector create(TraversalBranch startBranch, PathExpander expander) {
//            return new ArrivalBranchSelector(startBranch, expander);
//        }
//    }

}
