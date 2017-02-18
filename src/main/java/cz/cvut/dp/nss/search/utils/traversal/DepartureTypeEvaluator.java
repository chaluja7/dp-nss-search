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

    private final int departureMillisOfDay;

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

    private Long prevFoundedDeparture = null;

    public DepartureTypeEvaluator(String endStopName, LocalDateTime departureDateTime, int maxNumberOfResults) {
        this.endStopName = endStopName;
        this.departureMillisOfDay = departureDateTime.getMillisOfDay();
        this.maxNumberOfResults = maxNumberOfResults;
    }

    @Override
    public Evaluation evaluate(Path path) {
        Node startNode = path.startNode();
        long startNodeStopTimeId = (long) startNode.getProperty(StopTimeNode.STOP_TIME_ID_PROPERTY);
        long startNodeDeparture = (long) startNode.getProperty(StopTimeNode.DEPARTURE_PROPERTY);

        Node currentNode = path.endNode();
        String currentNodeStopName = (String) currentNode.getProperty(StopTimeNode.STOP_NAME_PROPERTY);

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

        Long currentNodeTimeProperty = currentNodeArrival != null ? currentNodeArrival : currentNodeDeparture;

        //POKUD jsem jiz na teto ceste (od start node) nasel cil v lepsim case
        if(foundedPaths.containsKey(startNodeStopTimeId)) {
            Long prevBestFoundedPathStart = foundedPaths.get(startNodeStopTimeId);
            long currentNodeMillisTimeWithPenalty = currentNodeTimeProperty + (DateTimeUtils.TRANSFER_PENALTY_SECONDS * foundedPathsNumOfTransfers.get(startNodeStopTimeId));
            if(currentNodeMillisTimeWithPenalty >= DateTimeUtils.SECONDS_IN_DAY) {
                //prehoupl jsem se s penalizaci do dalsiho dne
                currentNodeMillisTimeWithPenalty = currentNodeMillisTimeWithPenalty - DateTimeUtils.SECONDS_IN_DAY;
            }

            if(prevBestFoundedPathStart >= departureMillisOfDay) {
                //minuly nejlepsi cil byl pred pulnoci
                if((currentNodeMillisTimeWithPenalty > prevBestFoundedPathStart && currentNodeMillisTimeWithPenalty >= departureMillisOfDay) || currentNodeMillisTimeWithPenalty < departureMillisOfDay) {
                    //momentalne jsem taky pred pulnoci ale v horsim case nebo jsem az po pulnoci
                    return Evaluation.EXCLUDE_AND_PRUNE;
                }
            } else {
                //minuly nejlepsi cil byl po pulnoci
                if(currentNodeMillisTimeWithPenalty > prevBestFoundedPathStart && currentNodeMillisTimeWithPenalty < departureMillisOfDay) {
                    //momentalne jsem taky po pulnoci ale s horsim casem
                    return Evaluation.EXCLUDE_AND_PRUNE;
                }
            }
        }

        //vyhledavam na ceste, kde departureTime (vychoziho uzlu) je mensi, nez departure time nektere jiz nalezene cesty
        //to muzu hned ukoncit, protoze takovy vysledek stejne uzivateli nezobrazim
        if(prevFoundedDeparture != null && startNodeDeparture < prevFoundedDeparture) {
            return Evaluation.EXCLUDE_AND_PRUNE;
        }

        //uz jsem nasel pozadovany pocet vysledku
        if(foundedPathsDetails.size() >= maxNumberOfResults) {
            return Evaluation.EXCLUDE_AND_PRUNE;
        }

        //nasel jsem
        if(currentNodeStopName.equals(endStopName)) {
            Set<String> tmpTrips = new HashSet<>();
            for(Node n : path.nodes()) {
                tmpTrips.add((String) n.getProperty(StopTimeNode.TRIP_PROPERTY));
            }

            long currentNodeMillisTimeWithPenalty = currentNodeTimeProperty + (DateTimeUtils.TRANSFER_PENALTY_SECONDS * tmpTrips.size() - 1);
            if(currentNodeMillisTimeWithPenalty >= DateTimeUtils.SECONDS_IN_DAY) {
                //prehoupl jsem se s penalizaci do dalsiho dne
                currentNodeMillisTimeWithPenalty = currentNodeMillisTimeWithPenalty - DateTimeUtils.SECONDS_IN_DAY;
            }

            boolean saveMe = true;
            List<Long> keysToRemove = new ArrayList<>();
            for(Map.Entry<Long, Set<String>> entry : foundedPathsDetails.entrySet()) {
                Long key = entry.getKey();
                Set<String> value = entry.getValue();

                if(!Sets.intersection(tmpTrips, value).isEmpty()) {
                    Long pathArrival = foundedPaths.get(key);
                    if(pathArrival >= departureMillisOfDay) {
                        //cil aktualni cesty byl pred pulnoci
                        if((currentNodeMillisTimeWithPenalty >= departureMillisOfDay && currentNodeMillisTimeWithPenalty > pathArrival) || currentNodeMillisTimeWithPenalty < departureMillisOfDay) {
                            //momentalne jsem v cili taky pred pulnoci, ale s horsim casem nez jsem jiz byl, nebo jsem v cili az po pulnoci
                            saveMe = false;
                            break;
                        } else {
                            keysToRemove.add(key);
                        }
                    } else {
                        //cil aktualni cesty byl po pulnoci
                        if(currentNodeMillisTimeWithPenalty < departureMillisOfDay && currentNodeMillisTimeWithPenalty > pathArrival) {
                            //momentalne jsem taky po pulnoci ale pozdeji
                            saveMe = false;
                            break;
                        } else {
                            keysToRemove.add(key);
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
            foundedPaths.put(startNodeStopTimeId, currentNodeMillisTimeWithPenalty);
            foundedPathsNumOfTransfers.put(startNodeStopTimeId, tmpTrips.size() - 1);
            return Evaluation.INCLUDE_AND_PRUNE;
        }

        return Evaluation.EXCLUDE_AND_CONTINUE;
    }

}
