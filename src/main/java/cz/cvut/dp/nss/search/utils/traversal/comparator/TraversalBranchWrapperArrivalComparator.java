package cz.cvut.dp.nss.search.utils.traversal.comparator;

import cz.cvut.dp.nss.search.utils.traversal.wrapper.TraversalBranchWrapper;

import java.util.Comparator;

/**
 * Comparator to compare traversal branch wrappers in priority queue. For arrival finding type.
 *
 * @author jakubchalupa
 * @since 13.05.15
 */
public class TraversalBranchWrapperArrivalComparator implements Comparator<TraversalBranchWrapper> {

    @Override
    public int compare(TraversalBranchWrapper o1, TraversalBranchWrapper o2) {
        if(!o1.isOverMidnight() && o2.isOverMidnight()) {
            return -1;
        }

        if(o1.isOverMidnight() && !o2.isOverMidnight()) {
            return 1;
        }

        if(o1.getNodeTime() > o2.getNodeTime()) {
            return -1;
        }

        if(o1.getNodeTime() < o2.getNodeTime()) {
            return 1;
        }

        if(o1.getTravelTime() < o2.getTravelTime()) {
            return -1;
        }

        if(o1.getTravelTime() > o2.getTravelTime()) {
            return 1;
        }

        return 0;
    }

}
