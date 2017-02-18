package cz.cvut.dp.nss.search.utils;

import java.util.*;

/**
 * Util for deep collections cloning.
 *
 * @author jakubchalupa
 * @since 12.02.17
 */
public class CollectionCloneUtils {

    public static Set<String> cloneSet(Set<String> set) {
        Set<String> clone = new HashSet<>(set.size());
        for(String item : set) {
            clone.add(item);
        }

        return clone;
    }

    public static Map<String, List<String>> cloneMap(Map<String, List<String>> map) {
        Map<String, List<String>> clone = new HashMap<>(map.size());
        for (Map.Entry<String, List<String>> entry : map.entrySet()) {
            List<String> cloneList = new ArrayList<>();
            for(String item : entry.getValue()) {
                cloneList.add(item);
            }

            clone.put(entry.getKey(), cloneList);
        }

        return clone;
    }

}
