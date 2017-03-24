package cz.cvut.dp.nss.search.utils.comparator;

import cz.cvut.dp.nss.search.SearchResultWrapper;

import java.util.Comparator;

/**
 * Slouzi pro serazeni vysledku vyhledavani spojeni.
 *
 * @author jakubchalupa
 * @since 12.03.15
 */
public class SearchResultByDepartureDateComparator implements Comparator<SearchResultWrapper> {

    @Override
    public int compare(SearchResultWrapper o1, SearchResultWrapper o2) {

        if(!o1.isOverMidnightArrival() && o2.isOverMidnightArrival()) {
            return -1;
        }

        if(o1.isOverMidnightArrival() && !o2.isOverMidnightArrival()) {
            return 1;
        }

        if(o1.getArrival() < o2.getArrival()) {
            return -1;
        }

        if(o1.getArrival() > o2.getArrival()) {
            return 1;
        }

        if(o1.getDeparture() > o2.getDeparture()) {
            return -1;
        }

        if(o1.getDeparture() < o2.getDeparture()) {
            return 1;
        }

        if(o1.getNumberOfTransfers() < o2.getNumberOfTransfers()) {
            return -1;
        }

        if(o1.getNumberOfTransfers() > o2.getNumberOfTransfers()) {
            return 1;
        }

        return 0;
    }

}
