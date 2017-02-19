package cz.cvut.dp.nss.search.utils.comparator;

import cz.cvut.dp.nss.search.SearchResultWrapper;
import cz.cvut.dp.nss.search.utils.DateTimeUtils;

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

        //do razeni chci zakomponovat i penalizace za prestup! Toto funguje i pres pulnoc (dostanu se do zapornych cisel ale porovnani je stejne)
        long o1DepartureWithPenalty = o1.getDeparture() - (DateTimeUtils.TRANSFER_PENALTY_SECONDS * o1.getNumberOfTransfers());
        long o2DepartureWithPenalty = o2.getDeparture() - (DateTimeUtils.TRANSFER_PENALTY_SECONDS * o2.getNumberOfTransfers());

        if(o1DepartureWithPenalty > o2DepartureWithPenalty) {
            return -1;
        }

        if(o1DepartureWithPenalty < o2DepartureWithPenalty) {
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
