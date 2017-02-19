package cz.cvut.dp.nss.search.utils.traversal;

import com.google.common.collect.Sets;
import cz.cvut.dp.nss.search.entity.stopTime.StopTimeNode;
import cz.cvut.dp.nss.search.utils.DateTimeUtils;
import org.joda.time.LocalDateTime;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.traversal.Evaluation;
import org.neo4j.graphdb.traversal.Evaluator;

import java.util.*;

/**
 * Evaluator for neo4j traversal (finding paths to end station)
 *
 * @author jakubchalupa
 * @since 28.03.15 - 12.02.17
 */
public final class DepartureTypeEvaluator implements Evaluator {

    private final String endStopName;

    private final int departureSecondsOfDay;

    private final int maxNumberOfResults;

    /**
     * identifikator stopTimeId (start cesty) -> cas, ve kterem jsem z klice jiz nasel cilovou stanici (scope je v ramci cesty)
     */
    private final Map<Long, Long> foundedPaths = new HashMap<>();

    /**
     * identifikator stopTimeId (start cesty) -> pocet prestupu, ktere jsem vykonal do cilove stanice (scope je v ramci cesty)
     */
    private final Map<Long, Integer> foundedPathsNumOfTransfers = new HashMap<>();

    /**
     * identifikator stopTimeId (start cesty) -> set tripu, po kterych jsem nasel cil
     */
    private final Map<Long, Set<String>> foundedPathsDetails = new HashMap<>();

    /**
     * cas vyjezdu na ceste, na ktere jsem minule nasel cil
     */
    private Long prevFoundedDeparture = null;

    public DepartureTypeEvaluator(String endStopName, LocalDateTime departureDateTime, int maxNumberOfResults) {
        this.endStopName = endStopName;
        this.departureSecondsOfDay = departureDateTime.getMillisOfDay() / 1000;
        this.maxNumberOfResults = maxNumberOfResults;
    }

    @Override
    public Evaluation evaluate(Path path) {
        //informace z prvniho uzlu cesty (vychozi stanice)
        final Node startNode = path.startNode();
        final long startNodeStopTimeId = (long) startNode.getProperty(StopTimeNode.STOP_TIME_ID_PROPERTY);
        final long startNodeDeparture = (long) startNode.getProperty(StopTimeNode.DEPARTURE_PROPERTY);

        //informace o aktualnim uzlu cesty
        final Node currentNode = path.endNode();
        final String currentNodeStopName = (String) currentNode.getProperty(StopTimeNode.STOP_NAME_PROPERTY);

        //rozhodujici je arrival time pokud existuje, jinak departure time
        Long currentNodeArrival = null;
        Long currentNodeDeparture = null;
        if(currentNode.hasProperty(StopTimeNode.ARRIVAL_PROPERTY)) {
            currentNodeArrival = (Long) currentNode.getProperty(StopTimeNode.ARRIVAL_PROPERTY);
        }
        if(currentNode.hasProperty(StopTimeNode.DEPARTURE_PROPERTY)) {
            currentNodeDeparture = (Long) currentNode.getProperty(StopTimeNode.DEPARTURE_PROPERTY);
        }

        if(currentNodeArrival == null && currentNodeDeparture == null) {
            throw new RuntimeException("node must have departure or arrival time");
        }

        final long currentNodeTimeProperty = currentNodeArrival != null ? currentNodeArrival : currentNodeDeparture;

        //POKUD jsem jiz na teto ceste (od start node) nasel cil v lepsim case
        if(foundedPaths.containsKey(startNodeStopTimeId)) {
            //vytahnu si cas, v jakem jsem jiz nasel cil kde start byl startNodeStopTimeId
            final long prevBestFoundedPathStart = foundedPaths.get(startNodeStopTimeId);
            //a cas aktualniho uzlu pro porovnani, pokud uz bych byl s casem dal, nez v jakem case jsem jiz od
            //tohoto startu nasel cil, tak nema cenu dale traverzovat, protoze cil urcite jiz v lepsim case nenajdu
            long currentNodeTimeWithPenalty = currentNodeTimeProperty + (DateTimeUtils.TRANSFER_PENALTY_SECONDS * foundedPathsNumOfTransfers.get(startNodeStopTimeId));
            if(currentNodeTimeWithPenalty >= DateTimeUtils.SECONDS_IN_DAY) {
                //prehoupl jsem se s penalizaci do dalsiho dne
                currentNodeTimeWithPenalty = currentNodeTimeWithPenalty - DateTimeUtils.SECONDS_IN_DAY;
            }

            if(prevBestFoundedPathStart >= departureSecondsOfDay) {
                //minuly nejlepsi cil byl pred pulnoci
                if((currentNodeTimeWithPenalty > prevBestFoundedPathStart && currentNodeTimeWithPenalty >= departureSecondsOfDay) || currentNodeTimeWithPenalty < departureSecondsOfDay) {
                    //momentalne jsem taky pred pulnoci ale v horsim case nebo jsem az po pulnoci
                    return Evaluation.EXCLUDE_AND_PRUNE;
                }
            } else {
                //minuly nejlepsi cil byl po pulnoci
                if(currentNodeTimeWithPenalty > prevBestFoundedPathStart && currentNodeTimeWithPenalty < departureSecondsOfDay) {
                    //momentalne jsem taky po pulnoci ale s horsim casem
                    return Evaluation.EXCLUDE_AND_PRUNE;
                }
            }
        }

        //vyhledavam na ceste, kde departureTime (vychoziho uzlu) je mensi, nez departure time nektere jiz nalezene cesty
        //to muzu hned ukoncit, protoze takovy vysledek stejne uzivateli nezobrazim
        //nalezl bych pak totiz jedine vysledek, ktery vyjizdi drive a prijizdi pozdeji, nez nektera jiz nalezena cesta
        if(prevFoundedDeparture != null && startNodeDeparture < prevFoundedDeparture) {
            return Evaluation.EXCLUDE_AND_PRUNE;
        }

        //uz jsem nasel pozadovany pocet vysledku
        if(foundedPathsDetails.size() >= maxNumberOfResults) {
            return Evaluation.EXCLUDE_AND_PRUNE;
        }

        //nasel jsem
        if(currentNodeStopName.equals(endStopName)) {
            //do setu si ulozim vsechny jiz navstivene tripy po teto ceste
            final Set<String> tmpTrips = new HashSet<>();
            String tmpTrip;
            for(Node n : path.nodes()) {
                tmpTrip = (String) n.getProperty(StopTimeNode.TRIP_PROPERTY);
                if(!tmpTrips.contains(tmpTrip)) {
                    tmpTrips.add(tmpTrip);
                }
            }

            //zjistim cas aktualniho uzlu i s penalizaci za prestup
            long currentNodeTimeWithPenalty = currentNodeTimeProperty + (DateTimeUtils.TRANSFER_PENALTY_SECONDS * (tmpTrips.size() - 1));
            if(currentNodeTimeWithPenalty >= DateTimeUtils.SECONDS_IN_DAY) {
                //prehoupl jsem se s penalizaci do dalsiho dne
                currentNodeTimeWithPenalty = currentNodeTimeWithPenalty - DateTimeUtils.SECONDS_IN_DAY;
            }

            boolean saveMe = true;
            final List<Long> keysToRemove = new ArrayList<>();
            for(Map.Entry<Long, Set<String>> entry : foundedPathsDetails.entrySet()) {
                final long stopTimeId = entry.getKey();
                final Set<String> visitedTrips = entry.getValue();

                //TODO jakubchalupa - zde postupne mazu jiz nalezene cesty, ktere maji nejaky spolecny trip s aktualne nalezenou
                //TODO nebo naopak. Nemuze teoreticky nastat, ze smazu skoro vsechny a zustane jen jedna?
                //na aktualne nalezene ceste jsem jel alespon jednim stejnym tripem, jako na jiz drive nalezene ceste do cile
                //musim se tedy rozhodnout, zda je lepsi ta jiz drive nalezena cesta, nebo ta aktualni, protoze nechci mit obe
                if(!Sets.intersection(tmpTrips, visitedTrips).isEmpty()) {
                    //vytahnu si cas prijezdu na jit drive nalezene ceste
                    final long pathArrival = foundedPaths.get(stopTimeId);
                    if(pathArrival >= departureSecondsOfDay) {
                        //cil drive nalezene cesty byl pred pulnoci
                        if((currentNodeTimeWithPenalty >= departureSecondsOfDay && currentNodeTimeWithPenalty > pathArrival) || currentNodeTimeWithPenalty < departureSecondsOfDay) {
                            //momentalne jsem v cili taky pred pulnoci, ale s horsim casem nez jsem jiz byl, nebo jsem v cili az po pulnoci
                            //aktualne nalezenou cestu tedy nechci ukladat
                            saveMe = false;
                            break;
                        } else {
                            //naopak aktualne nalezena cesta je lepsi, nez ta drive nalezene, takze tu starou chci smazat
                            keysToRemove.add(stopTimeId);
                        }
                    } else {
                        //cil aktualni cesty byl po pulnoci
                        if(currentNodeTimeWithPenalty < departureSecondsOfDay && currentNodeTimeWithPenalty > pathArrival) {
                            //momentalne jsem taky po pulnoci ale pozdeji
                            //aktualne nalezenou cestu tedy nechci ukladat
                            saveMe = false;
                            break;
                        } else {
                            //naopak aktualni cesta je lepsi, takze tu starou mazu
                            keysToRemove.add(stopTimeId);
                        }
                    }
                }
            }

            for(Long l : keysToRemove) {
                foundedPathsDetails.remove(l);
            }

            if(saveMe) {
                foundedPathsDetails.put(startNodeStopTimeId, tmpTrips);
            }

            prevFoundedDeparture = startNodeDeparture;
            foundedPaths.put(startNodeStopTimeId, currentNodeTimeWithPenalty);
            foundedPathsNumOfTransfers.put(startNodeStopTimeId, tmpTrips.size() - 1);
            return Evaluation.INCLUDE_AND_PRUNE;
        }

        return Evaluation.EXCLUDE_AND_CONTINUE;
    }

}
