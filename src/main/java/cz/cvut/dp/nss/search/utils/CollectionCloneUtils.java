package cz.cvut.dp.nss.search.utils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Util for deep collections cloning.
 *
 * @author jakubchalupa
 * @since 12.02.17
 */
public class CollectionCloneUtils {

    public static Set<String> cloneSet(Set<String> set) {
        return new HashSet<>(set);
    }

    public static Map<String, Set<String>> cloneMap(Map<String, Set<String>> map) {
        final Map<String, Set<String>> clone = new HashMap<>(map.size());
        for(Map.Entry<String, Set<String>> entry : map.entrySet()) {
            final Set<String> cloneSet = new HashSet<>(entry.getValue());
            clone.put(entry.getKey(), cloneSet);
        }

        return clone;
    }

}
