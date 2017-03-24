package cz.cvut.dp.nss.search.utils.traversal;

import cz.cvut.dp.nss.search.entity.stopTime.StopTimeNode;
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

        //POKUD jsem jiz na teto ceste (od start node) nasel cil v lepsim case
        if(foundedPaths.containsKey(startNodeStopTimeId)) {
            //protoze iteruju dle casu tak uz urcine nemam sanci najit lepsi vysledek na teto ceste, nez jsem nasel driv
            return Evaluation.EXCLUDE_AND_PRUNE;
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
            if(!currentNode.hasProperty(StopTimeNode.ARRIVAL_PROPERTY)) throw new RuntimeException("arrival on target can not be null");
            Long currentNodeArrival = (Long) currentNode.getProperty(StopTimeNode.ARRIVAL_PROPERTY);

            //do setu si ulozim vsechny jiz navstivene tripy po teto ceste
            final Set<String> tmpTrips = new HashSet<>();
            fillVisitedTrips(tmpTrips, path);

            boolean saveMe = true;
            final List<Long> keysToRemove = new ArrayList<>();
            for(Map.Entry<Long, Set<String>> entry : foundedPathsDetails.entrySet()) {
                final long stopTimeId = entry.getKey();
                final Set<String> visitedTrips = entry.getValue();

                //TODO jakubchalupa - zde postupne mazu jiz nalezene cesty, ktere maji nejaky spolecny trip s aktualne nalezenou
                //TODO nebo naopak. Nemuze teoreticky nastat, ze smazu skoro vsechny a zustane jen jedna?
                //na aktualne nalezene ceste jsem jel alespon jednim stejnym tripem, jako na jiz drive nalezene ceste do cile
                //musim se tedy rozhodnout, zda je lepsi ta jiz drive nalezena cesta, nebo ta aktualni, protoze nechci mit obe
                if(!Collections.disjoint(tmpTrips, visitedTrips)) {
                    //vytahnu si cas prijezdu na jit drive nalezene ceste
                    final long pathArrival = foundedPaths.get(stopTimeId);
                    if(pathArrival >= departureSecondsOfDay) {
                        //cil drive nalezene cesty byl pred pulnoci
                        if((currentNodeArrival >= departureSecondsOfDay && currentNodeArrival > pathArrival) || currentNodeArrival < departureSecondsOfDay) {
                            //momentalne jsem v cili taky pred pulnoci, ale s horsim casem nez jsem jiz byl, nebo jsem v cili az po pulnoci
                            //aktualne nalezenou cestu tedy nechci ukladat
                            saveMe = false;
                            break;
                        } else {
                            //naopak aktualne nalezena cesta je lepsi, nez ta drive nalezene, takze tu starou chci smazat
                            keysToRemove.add(stopTimeId);
                        }
                    } else {
                        //cil drive nalezene cesty byl po pulnoci
                        if(currentNodeArrival < departureSecondsOfDay && currentNodeArrival > pathArrival) {
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

            prevFoundedDeparture = prevFoundedDeparture == null ? startNodeDeparture : Math.max(startNodeDeparture, prevFoundedDeparture);
            foundedPaths.put(startNodeStopTimeId, currentNodeArrival);
            return Evaluation.INCLUDE_AND_PRUNE;
        }

        return Evaluation.EXCLUDE_AND_CONTINUE;
    }

    private void fillVisitedTrips(Set<String> tmpTrips, Path path) {
        String tmpTrip;
        for(Node n : path.nodes()) {
            tmpTrip = (String) n.getProperty(StopTimeNode.TRIP_PROPERTY);
            if(!tmpTrips.contains(tmpTrip)) {
                tmpTrips.add(tmpTrip);
            }
        }
    }

}
